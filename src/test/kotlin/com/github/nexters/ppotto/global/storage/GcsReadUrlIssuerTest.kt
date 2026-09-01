package com.github.nexters.ppotto.global.storage

import com.github.nexters.ppotto.global.config.GcsConfig
import com.github.nexters.ppotto.global.config.GcsProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

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

        afterSpec {
            connectionFactory.destroy()
            redis.stop()
        }

        Given("같은 GCS 객체의 읽기 URL을 반복 발급할 때") {
            val objectKey = "stickers/analysis/sticker.png"

            When("서명 시각이 달라진 뒤 다시 발급하면") {
                val first = GcsReadUrlIssuer(storage, properties, redisTemplate).issue(listOf(objectKey)).getValue(objectKey)
                Thread.sleep(1_100)
                val second = GcsReadUrlIssuer(storage, properties, redisTemplate).issue(listOf(objectKey)).getValue(objectKey)

                Then("Valkey에 저장한 동일한 URL을 반환한다") {
                    second shouldBe first
                    redisTemplate.getExpire("gcs:read-url:${properties.bucket}:$objectKey").shouldBeGreaterThan(0)
                }
            }
        }

        Given("Valkey를 사용할 수 없을 때") {
            val unavailableConnectionFactory = LettuceConnectionFactory("127.0.0.1", 1).apply { afterPropertiesSet() }
            val unavailableRedisTemplate = StringRedisTemplate(unavailableConnectionFactory).apply { afterPropertiesSet() }

            afterContainer {
                unavailableConnectionFactory.destroy()
            }

            When("읽기 URL을 발급하면") {
                val url =
                    GcsReadUrlIssuer(storage, properties, unavailableRedisTemplate)
                        .issue(listOf("stickers/analysis/fallback.png"))
                        .values
                        .single()

                Then("캐시 없이 새 URL을 발급한다") {
                    url shouldContain "X-Goog-Expires=3600"
                }
            }
        }
    })
