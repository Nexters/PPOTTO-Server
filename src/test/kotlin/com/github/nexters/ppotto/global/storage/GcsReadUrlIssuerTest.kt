package com.github.nexters.ppotto.global.storage

import com.github.nexters.ppotto.global.config.GcsConfig
import com.github.nexters.ppotto.global.config.GcsProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.connection.RedisClusterConnection
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisSentinelConnection
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val CACHE_TTL_SECONDS = 55L * 60

class GcsReadUrlIssuerTest :
    BehaviorSpec({
        val redis =
            GenericContainer<Nothing>(DockerImageName.parse("redis:8.2-alpine")).apply {
                withExposedPorts(6379)
                start()
            }
        val connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379)).apply { afterPropertiesSet() }
        val redisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        val properties =
            GcsProperties(
                bucket = "ppotto-test-bucket",
                credentialsPath = "./src/test/resources/dummy-gcs-key.json",
                uploadSignedUrlExpirationMinutes = 15,
                readSignedUrlExpirationMinutes = 60,
                timeoutMillis = 5_000,
            )
        val storage = GcsConfig().storage(properties)
        val issuer = GcsReadUrlIssuer(storage, properties, redisTemplate)

        fun cacheKeyOf(objectKey: String): String = "gcs:read-url:${properties.bucket}:$objectKey"

        afterSpec {
            connectionFactory.destroy()
            redis.stop()
        }

        Given("아직 캐시된 적 없는 GCS 객체가 있을 때") {
            val objectKey = "stickers/analysis/${UUID.randomUUID()}.png"

            When("읽기 URL을 발급하면") {
                val url = issuer.issue(listOf(objectKey)).getValue(objectKey)

                Then("서명한 URL을 객체 키별 Valkey 키에 그대로 저장한다") {
                    redisTemplate.opsForValue().get(cacheKeyOf(objectKey)) shouldBe url
                }

                Then("만료 5분 전에 캐시가 먼저 끊기도록 55분 TTL을 건다") {
                    val ttl = redisTemplate.getExpire(cacheKeyOf(objectKey), TimeUnit.SECONDS)

                    ttl shouldBeGreaterThan CACHE_TTL_SECONDS - 60
                    ttl shouldBeLessThanOrEqual CACHE_TTL_SECONDS
                }

                Then("서명 URL의 만료는 설정한 60분이다") {
                    url shouldContain "X-Goog-Expires=3600"
                }
            }
        }

        Given("이미 캐시된 GCS 객체가 있을 때") {
            val objectKey = "stickers/analysis/${UUID.randomUUID()}.png"
            val cachedUrl = "https://storage.googleapis.com/cached-on-purpose/$objectKey"
            redisTemplate.opsForValue().set(cacheKeyOf(objectKey), cachedUrl)

            When("읽기 URL을 다시 발급하면") {
                val url = issuer.issue(listOf(objectKey)).getValue(objectKey)

                Then("새로 서명하지 않고 Valkey에 있던 URL을 그대로 반환한다") {
                    url shouldBe cachedUrl
                }
            }
        }

        Given("여러 객체 중 일부만 캐시되어 있을 때") {
            val cachedKey = "stickers/analysis/${UUID.randomUUID()}.png"
            val freshKey = "stickers/analysis/${UUID.randomUUID()}.png"
            val cachedUrl = "https://storage.googleapis.com/cached-on-purpose/$cachedKey"
            redisTemplate.opsForValue().set(cacheKeyOf(cachedKey), cachedUrl)

            When("두 객체의 읽기 URL을 한 번에 발급하면") {
                val urls = issuer.issue(listOf(cachedKey, freshKey))

                Then("캐시된 것은 그대로 쓰고 나머지만 새로 서명한다") {
                    urls.getValue(cachedKey) shouldBe cachedUrl
                    urls.getValue(freshKey) shouldContain "X-Goog-Signature="
                }
            }
        }

        Given("Valkey 연결이 실패할 때") {
            val unavailableRedisTemplate = StringRedisTemplate(UnavailableRedisConnectionFactory).apply { afterPropertiesSet() }

            When("읽기 URL을 발급하면") {
                val objectKey = "stickers/analysis/${UUID.randomUUID()}.png"
                val url =
                    GcsReadUrlIssuer(storage, properties, unavailableRedisTemplate)
                        .issue(listOf(objectKey))
                        .getValue(objectKey)

                Then("캐시를 건너뛰고 새 URL을 서명해 미디어 조회를 살린다") {
                    url shouldContain "X-Goog-Expires=3600"
                    url shouldContain "X-Goog-Signature="
                }
            }
        }
    })

private object UnavailableRedisConnectionFactory : RedisConnectionFactory {
    override fun getConvertPipelineAndTxResults(): Boolean = false

    override fun getConnection(): RedisConnection = throw failure()

    override fun getClusterConnection(): RedisClusterConnection = throw failure()

    override fun getSentinelConnection(): RedisSentinelConnection = throw failure()

    override fun translateExceptionIfPossible(ex: RuntimeException): DataAccessException? = ex as? DataAccessException

    private fun failure() = RedisConnectionFailureException("Valkey를 사용할 수 없습니다.")
}
