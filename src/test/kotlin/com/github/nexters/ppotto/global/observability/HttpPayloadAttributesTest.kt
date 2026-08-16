package com.github.nexters.ppotto.global.observability

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class HttpPayloadAttributesTest :
    BehaviorSpec({
        Given("민감한 헤더가 섞인 요청 헤더면") {
            val headers =
                mapOf(
                    "Authorization" to listOf("Bearer real-access-token"),
                    "Cookie" to listOf("session=abc"),
                    "X-API-Key" to listOf("secret-key"),
                    "Proxy-Authorization" to listOf("Basic zzz"),
                    "Set-Cookie" to listOf("a=b"),
                    "X-API-Version" to listOf("1"),
                    "Content-Type" to listOf("application/json"),
                )

            When("스팬 속성으로 변환하면") {
                val attributes = HttpPayloadAttributes.requestHeaders(headers)

                Then("OTel 규약대로 소문자 http.request.header.* 키를 쓴다") {
                    attributes shouldContainKey "http.request.header.authorization"
                    attributes shouldContainKey "http.request.header.x-api-version"
                }

                Then("민감 헤더 값은 코드에서 직접 마스킹한다") {
                    attributes["http.request.header.authorization"] shouldBe "[Filtered]"
                    attributes["http.request.header.cookie"] shouldBe "[Filtered]"
                    attributes["http.request.header.x-api-key"] shouldBe "[Filtered]"
                    attributes["http.request.header.proxy-authorization"] shouldBe "[Filtered]"
                    attributes["http.request.header.set-cookie"] shouldBe "[Filtered]"
                }

                Then("일반 헤더는 그대로 남긴다") {
                    attributes["http.request.header.x-api-version"] shouldBe "1"
                    attributes["http.request.header.content-type"] shouldBe "application/json"
                }
            }
        }

        Given("값이 여러 개인 헤더면") {
            val headers = mapOf("Accept-Encoding" to listOf("gzip", "deflate"))

            When("스팬 속성으로 변환하면") {
                val attributes = HttpPayloadAttributes.requestHeaders(headers)

                Then("배열이 아니라 콤마로 합친 스칼라 문자열로 만든다") {
                    attributes["http.request.header.accept-encoding"] shouldBe "gzip, deflate"
                }
            }
        }

        Given("응답 헤더면") {
            val headers = mapOf("Set-Cookie" to listOf("refresh=zzz"), "Content-Type" to listOf("application/json"))

            When("스팬 속성으로 변환하면") {
                val attributes = HttpPayloadAttributes.responseHeaders(headers)

                Then("http.response.header.* 키를 쓰고 민감 헤더를 마스킹한다") {
                    attributes["http.response.header.set-cookie"] shouldBe "[Filtered]"
                    attributes["http.response.header.content-type"] shouldBe "application/json"
                }
            }
        }

        Given("토큰이 담긴 JSON 응답 바디면") {
            val body =
                """
                {"data":{"accessToken":"eyJhbGciOiJIUzI1NiJ9.real","refreshToken":"real-refresh",
                "accessTokenExpiresIn":3600,"user":{"id":"u-1","name":"뽀또"}}}
                """.trimIndent()

            When("스팬 속성으로 변환하면") {
                val attributes = HttpPayloadAttributes.responseBody(body.toByteArray(), "application/json", "UTF-8")
                val recorded = attributes["http.response.body.data"] as String

                Then("토큰 값은 마스킹하고 나머지는 남긴다") {
                    recorded shouldContain "[Filtered]"
                    recorded shouldNotContain "eyJhbGciOiJIUzI1NiJ9.real"
                    recorded shouldNotContain "real-refresh"
                    recorded shouldContain "뽀또"
                    recorded shouldContain "3600"
                }

                Then("원본 바이트 크기를 함께 남긴다") {
                    attributes["http.response.body.size"] shouldBe body.toByteArray().size
                }
            }
        }

        Given("중첩 배열 안에 비밀번호가 있는 JSON 요청 바디면") {
            val body = """{"items":[{"password":"p@ss","label":"ok"}]}"""

            When("스팬 속성으로 변환하면") {
                val recorded =
                    HttpPayloadAttributes
                        .requestBody(body.toByteArray(), "application/json", null)["http.request.body.data"] as String

                Then("중첩 구조까지 훑어 마스킹한다") {
                    recorded shouldNotContain "p@ss"
                    recorded shouldContain "[Filtered]"
                    recorded shouldContain "ok"
                }
            }
        }

        Given("JSON이 아닌 바디면") {
            val body = "<html>large binary-ish payload</html>".toByteArray()

            When("스팬 속성으로 변환하면") {
                val attributes = HttpPayloadAttributes.responseBody(body, "text/html;charset=UTF-8", "UTF-8")

                Then("본문은 담지 않고 크기만 남긴다") {
                    attributes shouldNotContainKey "http.response.body.data"
                    attributes["http.response.body.size"] shouldBe body.size
                }
            }
        }

        Given("빈 바디면") {
            When("스팬 속성으로 변환하면") {
                Then("아무 속성도 만들지 않는다") {
                    HttpPayloadAttributes.requestBody(ByteArray(0), "application/json", null) shouldBe emptyMap()
                }
            }
        }

        Given("상한을 넘는 큰 JSON 바디면") {
            val stroke = List(20000) { """{"x":$it,"y":$it}""" }.joinToString(",")
            val body = """{"drawings":[$stroke]}"""

            When("스팬 속성으로 변환하면") {
                val attributes = HttpPayloadAttributes.requestBody(body.toByteArray(), "application/json", null)
                val recorded = attributes["http.request.body.data"] as String

                Then("상한에서 자르고 잘렸다는 표시를 남긴다") {
                    recorded.length shouldBe HttpPayloadAttributes.MAX_BODY_CHARS + "…[truncated]".length
                    recorded shouldContain "…[truncated]"
                }

                Then("자르기 전 원본 크기는 그대로 남긴다") {
                    attributes["http.request.body.size"] shouldBe body.toByteArray().size
                }
            }
        }

        Given("JSON이라고 했지만 파싱이 안 되는 바디면") {
            val body = "{not json".toByteArray()

            When("스팬 속성으로 변환하면") {
                val recorded =
                    HttpPayloadAttributes
                        .requestBody(body, "application/json", null)["http.request.body.data"] as String

                Then("원문을 그대로 남긴다") {
                    recorded shouldBe "{not json"
                }
            }
        }
    })
