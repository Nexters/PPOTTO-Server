package com.github.nexters.ppotto.auth.infrastructure.token

import com.github.nexters.ppotto.auth.config.JwtAuthProperties
import com.github.nexters.ppotto.global.identifier.UserId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

private const val REDIS_PORT = 6379
private const val USER_KEY_PREFIX = "auth:refresh:user:"

class RedisRefreshTokenStoreTest :
    BehaviorSpec({
        val redis =
            GenericContainer<Nothing>(DockerImageName.parse("redis:8.2-alpine"))
                .apply { withExposedPorts(REDIS_PORT) }
        lateinit var connectionFactory: LettuceConnectionFactory
        lateinit var template: StringRedisTemplate
        lateinit var store: RedisRefreshTokenStore

        beforeSpec {
            redis.start()
            connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(REDIS_PORT)).apply { afterPropertiesSet() }
            template = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
            store =
                RedisRefreshTokenStore(
                    template,
                    JwtAuthProperties(
                        issuer = "ppotto-test",
                        secret = "test-secret-that-is-at-least-32-bytes-long",
                        accessTokenExpirationSeconds = 3600,
                        refreshTokenExpirationDays = 30,
                    ),
                )
        }

        afterSpec {
            connectionFactory.destroy()
            redis.stop()
        }

        Given("refresh token이 저장된 상태에서") {
            val userId = UserId(UUID.randomUUID())
            val currentToken = "rotate-current-${UUID.randomUUID()}"
            val newToken = "rotate-new-${UUID.randomUUID()}"
            store.save(userId, currentToken)

            When("새 refresh token으로 rotation하면") {
                val rotated = store.rotate(userId, currentToken, newToken)

                Then("rotation에 성공한다") {
                    rotated.shouldBeTrue()
                }

                Then("기존 토큰은 더 이상 사용자에 연결되지 않는다") {
                    store.findUserId(currentToken).shouldBeNull()
                }

                Then("새 토큰이 사용자에 연결된다") {
                    store.findUserId(newToken) shouldBe userId
                }
            }
        }

        Given("이미 한 번 회전한 refresh token이 재사용될 때") {
            val userId = UserId(UUID.randomUUID())
            val currentToken = "replay-current-${UUID.randomUUID()}"
            val newToken = "replay-new-${UUID.randomUUID()}"
            store.save(userId, currentToken)
            store.rotate(userId, currentToken, newToken)

            When("같은 refresh token으로 다시 rotation하면") {
                val replayed = store.rotate(userId, currentToken, "replayed-${UUID.randomUUID()}")

                Then("rotation을 거부한다") {
                    replayed.shouldBeFalse()
                }

                Then("직전에 발급된 토큰은 그대로 살아 있다") {
                    store.findUserId(newToken) shouldBe userId
                }
            }
        }

        Given("로그아웃할 사용자의 refresh token이 저장되어 있을 때") {
            val userId = UserId(UUID.randomUUID())
            val currentToken = "delete-current-${UUID.randomUUID()}"
            val newToken = "delete-new-${UUID.randomUUID()}"
            store.save(userId, currentToken)
            store.rotate(userId, currentToken, newToken)

            When("사용자의 refresh token을 삭제하면") {
                store.delete(userId)

                Then("현재 인덱싱된 token으로 사용자를 찾을 수 없다") {
                    store.findUserId(newToken).shouldBeNull()
                }

                Then("사용자 index key 자체가 사라진다") {
                    template.hasKey("$USER_KEY_PREFIX$userId") shouldBe false
                }
            }
        }

        Given("refresh token 원문을 저장할 때") {
            val userId = UserId(UUID.randomUUID())
            val rawToken = "raw-refresh-token-${UUID.randomUUID()}"
            store.save(userId, rawToken)

            When("Redis의 모든 key와 값을 훑으면") {
                val keys = template.keys("*")
                val values = keys.mapNotNull { template.opsForValue().get(it) }

                Then("원문이 key에 노출되지 않는다") {
                    keys.forAll { it shouldNotContain rawToken }
                }

                Then("원문이 값에도 노출되지 않는다") {
                    values.forAll { it shouldNotContain rawToken }
                }
            }
        }
    })
