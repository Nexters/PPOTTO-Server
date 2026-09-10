package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.config.VertexAiProperties
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.google.genai.Client
import com.google.genai.types.HttpOptions
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.util.UUID

class VertexAiGeminiClassifierFailureTest :
    BehaviorSpec({
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var status = 200
        var responseText = "{}"
        server.createContext("/") { exchange ->
            exchange.requestBody.readBytes()
            val body = responseText.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val client =
            Client
                .builder()
                .vertexAI(false)
                .apiKey("dummy-test-key")
                .httpOptions(
                    HttpOptions
                        .builder()
                        .baseUrl("http://127.0.0.1:${server.address.port}")
                        .build(),
                ).build()
        val classifier =
            VertexAiGeminiClassifier(
                client,
                JsonMapper(),
                VertexAiProperties("test", "test", 5_000, 5_000),
            )
        val photos = listOf(PhotoRef(PhotoId(UUID.randomUUID()), "gs://test/photo.jpg", "image/jpeg"))

        afterSpec {
            client.close()
            server.stop(0)
        }

        Given("분류 요청을 외부 서비스가 거절하면") {
            When("사진을 분류하면") {
                Then("호출 실패 코드로 반환한다") {
                    status = 400
                    responseText = """{"error":{"code":400,"message":"provider failure","status":"INVALID_ARGUMENT"}}"""
                    shouldThrow<BusinessException> { classifier.classifyAndRecap(photos) }.errorCode.code shouldBe "ANALYSIS-018"
                }
            }
        }

        Given("분류 호출은 성공했지만 응답 내용이 잘못되면") {
            When("사진을 분류하면") {
                Then("호출 실패와 구분되는 응답 오류 코드로 반환한다") {
                    status = 200
                    listOf("not-json", "[]", "null", "[null]").forEach { generatedText ->
                        responseText = """{"candidates":[{"content":{"role":"model","parts":[{"text":"$generatedText"}]}}]}"""
                        shouldThrow<BusinessException> { classifier.classifyAndRecap(photos) }.errorCode.code shouldBe "ANALYSIS-007"
                    }
                }
            }
        }
    })
