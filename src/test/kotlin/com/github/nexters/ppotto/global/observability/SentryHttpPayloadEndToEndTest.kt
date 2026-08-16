package com.github.nexters.ppotto.global.observability

import com.github.nexters.ppotto.support.ObjectStorageTestConfiguration
import com.github.nexters.ppotto.support.TestcontainersConfiguration
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.sentry.SentryLogEvent
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "SENTRY_DSN=https://public@localhost/1",
        "SENTRY_TRACES_SAMPLE_RATE=1.0",
    ],
)
@ActiveProfiles("test")
@Import(
    TestcontainersConfiguration::class,
    ObjectStorageTestConfiguration::class,
    SentryHttpPayloadEndToEndTest.PayloadTestConfiguration::class,
)
class SentryHttpPayloadEndToEndTest(
    environment: Environment,
    recorder: RecordingTransactions,
    logRecorder: RecordingLogs,
) : BehaviorSpec({
        val port = environment.getProperty("local.server.port")
        val client = HttpClient.newHttpClient()

        fun post(
            path: String,
            body: String,
            bearer: String? = null,
        ): HttpResponse<String> =
            client.send(
                HttpRequest
                    .newBuilder(URI.create("http://localhost:$port$path"))
                    .header("Content-Type", "application/json")
                    .header("Cookie", "session=real-cookie-value")
                    .apply { bearer?.let { header("Authorization", "Bearer $it") } }
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        fun spanDataOf(marker: String): Map<String, Any> =
            recorder.transactions
                .mapNotNull {
                    it.contexts.trace
                        ?.data
                }.lastOrNull { (it["http.request.body.data"] as? String)?.contains(marker) == true }
                ?: error(
                    "no transaction carrying marker=$marker; captured=${recorder.transactions.size}; " +
                        "keys=${recorder.transactions.map {
                            it.contexts.trace
                                ?.data
                                ?.keys
                                ?.sorted()
                        }}",
                )

        Given("컨트롤러가 본문을 읽는 요청이면") {
            val body = """{"provider":"KAKAO","accessToken":"secret-oauth-token","name":"본문읽힘마커"}"""

            When("실제 톰캣으로 요청을 보내면") {
                post("/auth/login", body)

                Then("요청 본문이 마스킹되어 span에 담긴다") {
                    val recorded = spanDataOf("본문읽힘마커")["http.request.body.data"] as? String

                    recorded.shouldNotBeNull()
                    recorded shouldContain "본문읽힘마커"
                    recorded shouldNotContain "secret-oauth-token"
                }

                Then("응답 본문도 span에 담긴다") {
                    (spanDataOf("본문읽힘마커")["http.response.body.data"] as? String).shouldNotBeNull()
                }
            }
        }

        Given("시큐리티 필터가 본문을 읽기 전에 401로 끊는 요청이면") {
            val body = """{"refreshToken":"test-value-for-scrubbing-check","name":"조기거부마커"}"""

            When("잘못된 Bearer 토큰으로 요청을 보내면") {
                val response = post("/boards", body, bearer = "not-a-real-jwt")

                Then("401로 끊긴다") {
                    response.statusCode() shouldBe 401
                }

                Then("읽히지 않은 요청 본문도 span에 담긴다") {
                    val recorded = spanDataOf("조기거부마커")["http.request.body.data"] as? String

                    recorded.shouldNotBeNull()
                    recorded shouldContain "조기거부마커"
                    recorded shouldNotContain "test-value-for-scrubbing-check"
                }
            }
        }

        Given("매핑되지 않은 경로로 본문을 보내면") {
            val body = """{"refreshToken":"test-value-for-scrubbing-check","name":"미매핑마커"}"""

            When("실제 톰캣으로 요청을 보내면") {
                post("/api/v1/auth/refresh", body)

                Then("컨트롤러가 없어도 요청 본문이 span에 담긴다") {
                    val recorded = spanDataOf("미매핑마커")["http.request.body.data"] as? String

                    recorded.shouldNotBeNull()
                    recorded shouldContain "미매핑마커"
                    recorded shouldNotContain "test-value-for-scrubbing-check"
                }
            }
        }

        Given("정상 요청이면") {
            When("실제 톰캣으로 요청을 보내면") {
                post("/auth/login", """{"provider":"KAKAO","name":"예산마커"}""")

                Then("바디 4개 속성이 모두 담긴다") {
                    val ours = spanDataOf("예산마커").keys.filter { it.startsWith("http.") }

                    ours shouldContainAll
                        listOf(
                            "http.request.body.data",
                            "http.request.body.size",
                            "http.response.body.data",
                            "http.response.body.size",
                        )
                }

                Then("헤더는 개별 http.*.header.* 속성으로 담긴다") {
                    val data = spanDataOf("예산마커")

                    data.keys.any { it.startsWith("http.request.header.") } shouldBe true
                    data.keys.any { it.startsWith("http.response.header.") } shouldBe true
                    data["http.request.header.content-type"] shouldBe "application/json"
                }

                Then("모든 속성 값이 스칼라다") {
                    spanDataOf("예산마커").forEach { (key, value) ->
                        withClue(key) { (value is String || value is Number || value is Boolean) shouldBe true }
                    }
                }

                Then("민감 헤더는 마스킹된다") {
                    val data = spanDataOf("예산마커")

                    data["http.request.header.cookie"] shouldBe "[Filtered]"
                    data.values.none { it is String && it.contains("real-cookie-value") } shouldBe true
                }
            }
        }

        Given("애플리케이션이 INFO 로그를 남기면") {
            When("실제 요청을 처리하면") {
                post("/auth/login", """{"provider":"KAKAO","name":"로그마커"}""")

                Then("Sentry Logs로 로그 레코드가 흘러간다") {
                    logRecorder.logs.shouldNotBeEmpty()
                }

                Then("INFO 레벨 로그가 포함된다") {
                    logRecorder.logs
                        .map { it.level }
                        .toSet() shouldContain SentryLogLevel.INFO
                }

                Then("요청 로그 본문이 담긴다") {
                    logRecorder.logs
                        .any { it.body?.contains("/auth/login") == true } shouldBe true
                }
            }
        }
    }) {
    @TestConfiguration(proxyBeanMethods = false)
    class PayloadTestConfiguration {
        @Bean
        fun recordingTransactions(): RecordingTransactions = RecordingTransactions()

        @Bean
        fun recordingBeforeSendTransaction(recorder: RecordingTransactions): SentryOptions.BeforeSendTransactionCallback =
            SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
                recorder.transactions.add(transaction)
                null
            }

        @Bean
        fun droppingBeforeSend(): SentryOptions.BeforeSendCallback = SentryOptions.BeforeSendCallback { _, _ -> null }

        @Bean
        fun recordingLogs(): RecordingLogs = RecordingLogs()

        @Bean
        fun recordingBeforeSendLog(recorder: RecordingLogs): SentryOptions.Logs.BeforeSendLogCallback =
            SentryOptions.Logs.BeforeSendLogCallback { log ->
                recorder.logs.add(log)
                null
            }
    }
}

class RecordingTransactions {
    val transactions: MutableList<SentryTransaction> = CopyOnWriteArrayList()
}

class RecordingLogs {
    val logs: MutableList<SentryLogEvent> = CopyOnWriteArrayList()
}
