package com.github.nexters.ppotto.auth.infrastructure.token

import com.github.nexters.ppotto.auth.config.JwtAuthProperties
import com.github.nexters.ppotto.global.identifier.UserId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

class RedisRefreshTokenStoreTest :
    BehaviorSpec({
        val redis =
            GenericContainer<Nothing>(DockerImageName.parse("redis:8.2-alpine")).apply {
                withExposedPorts(6379)
                start()
            }
        val connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379)).apply { afterPropertiesSet() }
        val template = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        val store =
            RedisRefreshTokenStore(
                template,
                JwtAuthProperties(
                    issuer = "ppotto-test",
                    secret = "test-secret-that-is-at-least-32-bytes-long",
                    accessTokenExpirationSeconds = 3600,
                    refreshTokenExpirationDays = 30,
                ),
            )

        afterSpec {
            connectionFactory.destroy()
            redis.stop()
        }

        Given("refresh token이 저장된 상태에서") {
            val userId = UserId(UUID.randomUUID())
            val currentToken = "current-refresh-token"
            store.save(userId, currentToken)

            When("새 refresh token으로 rotation하면") {
                val newToken = "new-refresh-token"

                Then("기존 토큰은 한 번만 교체되고 새 토큰만 유효하다") {
                    store.rotate(userId, currentToken, newToken) shouldBe true
                    store.rotate(userId, currentToken, "replayed-token") shouldBe false
                    store.findUserId(currentToken) shouldBe null
                    store.findUserId(newToken) shouldBe userId
                }
            }

            When("사용자의 refresh token을 삭제하면") {
                Then("token index와 사용자 index가 모두 삭제된다") {
                    store.delete(userId)

                    store.findUserId(currentToken) shouldBe null
                }
            }
        }

        Given("refresh token 원문을 저장할 때") {
            val userId = UserId(UUID.randomUUID())
            val rawToken = "raw-refresh-token"

            When("Redis key를 조회하면") {
                Then("원문이 key에 노출되지 않는다") {
                    store.save(userId, rawToken)

                    template.keys("*").none { it.contains(rawToken) } shouldBe true
                }
            }
        }
    })
