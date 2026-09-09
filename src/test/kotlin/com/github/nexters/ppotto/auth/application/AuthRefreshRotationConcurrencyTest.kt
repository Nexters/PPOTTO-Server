package com.github.nexters.ppotto.auth.application

import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.config.JwtAuthProperties
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.infrastructure.sha256Hex
import com.github.nexters.ppotto.auth.infrastructure.token.JwtTokenProvider
import com.github.nexters.ppotto.auth.infrastructure.token.RedisRefreshTokenStore
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.support.runConcurrently
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.transaction.support.TransactionOperations
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

private const val REDIS_PORT = 6379
private const val USER_KEY_PREFIX = "auth:refresh:user:"

class AuthRefreshRotationConcurrencyTest :
    BehaviorSpec({
        val redis =
            GenericContainer<Nothing>(DockerImageName.parse("redis:8.2-alpine"))
                .apply { withExposedPorts(REDIS_PORT) }
        lateinit var connectionFactory: LettuceConnectionFactory
        lateinit var template: StringRedisTemplate
        lateinit var store: RedisRefreshTokenStore
        lateinit var authService: AuthService

        beforeSpec {
            redis.start()
            connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(REDIS_PORT)).apply { afterPropertiesSet() }
            template = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
            val properties =
                JwtAuthProperties(
                    issuer = "ppotto-test",
                    secret = "test-secret-that-is-at-least-32-bytes-long",
                    accessTokenExpirationSeconds = 3600,
                    refreshTokenExpirationDays = 30,
                )
            store = RedisRefreshTokenStore(template, properties)
            authService =
                AuthService(
                    oauthClients = emptyList(),
                    signupTransaction = TransactionOperations.withoutTransaction(),
                    tokenProvider = JwtTokenProvider(properties),
                    refreshTokenStore = store,
                    authUserPort = AuthUserPort { null },
                    authTermsPort = AuthTermsPort { emptyList() },
                    authActiveUserPort = AuthActiveUserPort { true },
                )
        }

        afterSpec {
            connectionFactory.destroy()
            redis.stop()
        }

        Given("같은 refresh token을 가진 재발급 요청이 동시에 들어올 때") {
            val userId = UserId(UUID.randomUUID())
            val refreshToken = "concurrent-refresh-${UUID.randomUUID()}"
            store.save(userId, refreshToken)

            When("두 요청이 동시에 재발급을 시도하면") {
                val results = runConcurrently(2) { authService.refresh(refreshToken) }
                val issued = results.mapNotNull { it.getOrNull() }
                val failures = results.mapNotNull { it.exceptionOrNull() }

                Then("Lua 회전 스크립트가 한 요청만 통과시킨다") {
                    issued shouldHaveSize 1
                }

                Then("나머지 요청은 AUTH-002로 거절된다") {
                    failures shouldHaveSize 1
                    failures
                        .single()
                        .shouldBeInstanceOf<UnauthorizedException>()
                        .errorCode shouldBe AuthErrorCode.INVALID_REFRESH_TOKEN
                }

                Then("성공한 요청이 발급한 token만 사용자에 연결된다") {
                    store.findUserId(issued.single().refreshToken) shouldBe userId
                }

                Then("소진된 refresh token은 더 이상 사용자에 연결되지 않는다") {
                    store.findUserId(refreshToken).shouldBeNull()
                }
            }
        }

        Given("token index는 살아 있는데 사용자 index가 다른 token을 가리킬 때") {
            val userId = UserId(UUID.randomUUID())
            val refreshToken = "stale-index-${UUID.randomUUID()}"
            store.save(userId, refreshToken)
            template
                .opsForValue()
                .set("$USER_KEY_PREFIX$userId", "another-token".sha256Hex())

            When("그 refresh token으로 재발급을 요청하면") {
                val exception = shouldThrow<UnauthorizedException> { authService.refresh(refreshToken) }

                Then("회전 거절을 AUTH-002로 변환한다") {
                    exception.errorCode shouldBe AuthErrorCode.INVALID_REFRESH_TOKEN
                }

                Then("사용자 index는 회전되지 않은 채 남는다") {
                    template
                        .opsForValue()
                        .get("$USER_KEY_PREFIX$userId") shouldBe "another-token".sha256Hex()
                }
            }
        }
    })
