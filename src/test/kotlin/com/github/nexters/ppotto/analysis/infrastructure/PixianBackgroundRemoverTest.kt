package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.global.config.PixianProperties
import com.github.nexters.ppotto.global.error.BusinessException
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class PixianBackgroundRemoverTest :
    BehaviorSpec({
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val requestCount = AtomicInteger(0)
        var responsesByAttempt = listOf(200 to byteArrayOf(1, 2, 3, 4))
        var lastRequestBody = ByteArray(0)
        server.createContext("/remove-background") { exchange ->
            lastRequestBody = exchange.requestBody.readBytes()
            val attempt = requestCount.getAndIncrement()
            val (status, body) = responsesByAttempt.getOrElse(attempt) { responsesByAttempt.last() }
            exchange.responseHeaders.add("X-Credits-Charged", "0.05")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        val baseUri = "http://localhost:${server.address.port}"
        val remover =
            PixianBackgroundRemover(
                HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(RestClient.builder().build()))
                    .build()
                    .createClient(PixianApi::class.java),
                PixianProperties(
                    apiId = "test-id",
                    apiSecret = "test-secret",
                    testMode = true,
                    removeBackgroundUri = URI("$baseUri/remove-background"),
                ),
            )

        afterSpec {
            server.stop(0)
        }

        Given("Pixian이 배경 제거된 이미지를 정상 반환할 때") {
            requestCount.set(0)
            responsesByAttempt = listOf(200 to byteArrayOf(9, 9, 9))

            When("removeBackground를 호출하면") {
                Then("응답 바이트를 그대로 반환한다") {
                    remover.removeBackground(byteArrayOf(1), "image/jpeg") shouldBe byteArrayOf(9, 9, 9)
                }
            }
        }

        Given("Pixian이 4xx 에러를 반환할 때") {
            requestCount.set(0)
            responsesByAttempt = listOf(400 to byteArrayOf())

            When("removeBackground를 호출하면") {
                Then("재시도 없이 즉시 ANALYSIS-011 예외가 발생한다") {
                    val exception =
                        shouldThrow<BusinessException> {
                            remover.removeBackground(byteArrayOf(1), "image/jpeg")
                        }
                    exception.errorCode shouldBe AnalysisErrorCode.STICKER_BACKGROUND_REMOVAL_FAILED
                    requestCount.get() shouldBe 1
                }
            }
        }

        Given("Pixian이 첫 요청에서 5xx를 반환하고 재시도에서 성공할 때") {
            requestCount.set(0)
            responsesByAttempt = listOf(503 to byteArrayOf(), 200 to byteArrayOf(7, 7, 7))

            When("removeBackground를 호출하면") {
                Then("1회 재시도해서 성공 응답을 반환한다") {
                    remover.removeBackground(byteArrayOf(1), "image/jpeg") shouldBe byteArrayOf(7, 7, 7)
                    requestCount.get() shouldBe 2
                }
            }
        }

        Given("Pixian이 재시도까지 계속 5xx를 반환할 때") {
            requestCount.set(0)
            responsesByAttempt = listOf(503 to byteArrayOf())

            When("removeBackground를 호출하면") {
                Then("정확히 2번만 시도하고 ANALYSIS-011 예외가 발생한다") {
                    val exception =
                        shouldThrow<BusinessException> {
                            remover.removeBackground(byteArrayOf(1), "image/jpeg")
                        }
                    exception.errorCode shouldBe AnalysisErrorCode.STICKER_BACKGROUND_REMOVAL_FAILED
                    requestCount.get() shouldBe 2
                }
            }
        }

        Given("원본 사진의 mimeType이 image/jpeg일 때") {
            requestCount.set(0)
            responsesByAttempt = listOf(200 to byteArrayOf(9, 9, 9))

            When("removeBackground를 호출하면") {
                Then("jpg 확장자가 붙은 파일명으로 전송한다") {
                    remover.removeBackground(byteArrayOf(1, 2, 3), "image/jpeg")

                    String(lastRequestBody, Charsets.ISO_8859_1) shouldContain "filename=\"source-image.jpg\""
                }
            }
        }

        Given("원본 사진의 mimeType이 image/png일 때") {
            requestCount.set(0)
            responsesByAttempt = listOf(200 to byteArrayOf(9, 9, 9))

            When("removeBackground를 호출하면") {
                Then("png 확장자가 붙은 파일명으로 전송한다") {
                    remover.removeBackground(byteArrayOf(1, 2, 3), "image/png")

                    String(lastRequestBody, Charsets.ISO_8859_1) shouldContain "filename=\"source-image.png\""
                }
            }
        }

        Given("원본 사진의 mimeType이 image/webp일 때") {
            requestCount.set(0)
            responsesByAttempt = listOf(200 to byteArrayOf(9, 9, 9))

            When("removeBackground를 호출하면") {
                Then("webp 확장자가 붙은 파일명으로 전송한다") {
                    remover.removeBackground(byteArrayOf(1, 2, 3), "image/webp")

                    String(lastRequestBody, Charsets.ISO_8859_1) shouldContain "filename=\"source-image.webp\""
                }
            }
        }

        Given("원본 사진의 mimeType을 알 수 없을 때") {
            requestCount.set(0)
            responsesByAttempt = listOf(200 to byteArrayOf(9, 9, 9))

            When("removeBackground를 호출하면") {
                Then("기본 확장자(png)로 폴백해서 요청을 보낸다") {
                    remover.removeBackground(byteArrayOf(1, 2, 3), "image/unknown")

                    String(lastRequestBody, Charsets.ISO_8859_1) shouldContain "filename=\"source-image.png\""
                }
            }
        }
    })
