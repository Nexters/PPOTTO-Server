package com.github.nexters.ppotto.global.observability

import com.github.nexters.ppotto.support.ObjectStorageTestConfiguration
import com.github.nexters.ppotto.support.TestcontainersConfiguration
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val ARRIVAL_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10)

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

        Given("컨트롤러가 본문을 읽는 요청이면") {
            val body = """{"provider":"KAKAO","accessToken":"secret-oauth-token","name":"본문읽힘마커"}"""

            When("실제 톰캣으로 요청을 보내면") {
                post("/auth/login", body)
                val data = recorder.awaitSpanData("본문읽힘마커")

                Then("요청 본문이 마스킹되어 span에 담긴다") {
                    val recorded = data["http.request.body.data"] as? String

                    recorded.shouldNotBeNull()
                    recorded shouldContain "본문읽힘마커"
                    recorded shouldNotContain "secret-oauth-token"
                }

                Then("응답 본문도 span에 담긴다") {
                    (data["http.response.body.data"] as? String).shouldNotBeNull()
                }
            }
        }

        Given("시큐리티 필터가 본문을 읽기 전에 401로 끊는 요청이면") {
            val body = """{"refreshToken":"test-value-for-scrubbing-check","name":"조기거부마커"}"""

            When("잘못된 Bearer 토큰으로 요청을 보내면") {
                val response = post("/boards", body, bearer = "not-a-real-jwt")
                val data = recorder.awaitSpanData("조기거부마커")

                Then("401로 끊긴다") {
                    response.statusCode() shouldBe 401
                }

                Then("읽히지 않은 요청 본문도 span에 담긴다") {
                    val recorded = data["http.request.body.data"] as? String

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
                val data = recorder.awaitSpanData("미매핑마커")

                Then("컨트롤러가 없어도 요청 본문이 span에 담긴다") {
                    val recorded = data["http.request.body.data"] as? String

                    recorded.shouldNotBeNull()
                    recorded shouldContain "미매핑마커"
                    recorded shouldNotContain "test-value-for-scrubbing-check"
                }
            }
        }

        Given("정상 요청이면") {
            When("실제 톰캣으로 요청을 보내면") {
                post("/auth/login", """{"provider":"KAKAO","name":"예산마커"}""")
                val data = recorder.awaitSpanData("예산마커")

                Then("바디 4개 속성이 모두 담긴다") {
                    data.keys.filter { it.startsWith("http.") } shouldContainAll
                        listOf(
                            "http.request.body.data",
                            "http.request.body.size",
                            "http.response.body.data",
                            "http.response.body.size",
                        )
                }

                Then("헤더는 개별 http.*.header.* 속성으로 담긴다") {
                    data.keys.any { it.startsWith("http.request.header.") } shouldBe true
                    data.keys.any { it.startsWith("http.response.header.") } shouldBe true
                    data["http.request.header.content-type"] shouldBe "application/json"
                }

                Then("모든 속성 값이 스칼라다") {
                    data.forEach { (key, value) ->
                        withClue(key) { (value is String || value is Number || value is Boolean) shouldBe true }
                    }
                }

                Then("민감 헤더는 마스킹된다") {
                    data["http.request.header.cookie"] shouldBe "[Filtered]"
                    data.values.none { it is String && it.contains("real-cookie-value") } shouldBe true
                }
            }
        }

        Given("애플리케이션이 INFO 로그를 남기면") {
            When("실제 요청을 처리하면") {
                post("/auth/login", """{"provider":"KAKAO","name":"로그마커"}""")

                Then("INFO 레벨 요청 로그가 Sentry Logs로 흘러간다") {
                    val log =
                        logRecorder.awaitLog("/auth/login 요청 로그") {
                            it.level == SentryLogLevel.INFO && it.body?.contains("/auth/login") == true
                        }

                    log.level shouldBe SentryLogLevel.INFO
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
                recorder.record(transaction)
                null
            }

        @Bean
        fun droppingBeforeSend(): SentryOptions.BeforeSendCallback = SentryOptions.BeforeSendCallback { _, _ -> null }

        @Bean
        fun recordingLogs(): RecordingLogs = RecordingLogs()

        @Bean
        fun recordingBeforeSendLog(recorder: RecordingLogs): SentryOptions.Logs.BeforeSendLogCallback =
            SentryOptions.Logs.BeforeSendLogCallback { log ->
                recorder.record(log)
                null
            }
    }
}

/**
 * Sentry SDK가 트랜잭션/로그를 자체 큐에서 비동기로 넘기고, 서버 스레드는 응답 커밋 뒤에 트랜잭션을 닫는다.
 * 이 spec에서만 진짜 비동기가 존재하므로, 폴링(`eventually`) 대신 도착 시 깨우는 조건 변수로 기다린다.
 */
class ArrivalBuffer<T> {
    private val lock = ReentrantLock()
    private val arrived = lock.newCondition()
    private val recorded = mutableListOf<T>()

    fun record(item: T) {
        lock.withLock {
            recorded += item
            arrived.signalAll()
        }
    }

    fun <R : Any> await(
        description: String,
        extract: (List<T>) -> R?,
    ): R =
        lock.withLock {
            var remaining = ARRIVAL_TIMEOUT_NANOS
            var found = extract(recorded)
            while (found == null) {
                check(remaining > 0) { "$description 이(가) 10초 안에 Sentry로 전달되지 않았습니다. 수집된 항목=${recorded.size}" }
                remaining = arrived.awaitNanos(remaining)
                found = extract(recorded)
            }
            found
        }
}

class RecordingTransactions {
    private val buffer = ArrivalBuffer<SentryTransaction>()

    fun record(transaction: SentryTransaction) = buffer.record(transaction)

    fun awaitSpanData(marker: String): Map<String, Any> =
        buffer.await("요청 본문에 $marker 를 담은 트랜잭션") { transactions ->
            transactions
                .mapNotNull {
                    it.contexts.trace
                        ?.data
                }.lastOrNull { (it["http.request.body.data"] as? String)?.contains(marker) == true }
        }
}

class RecordingLogs {
    private val buffer = ArrivalBuffer<SentryLogEvent>()

    fun record(log: SentryLogEvent) = buffer.record(log)

    fun awaitLog(
        description: String,
        predicate: (SentryLogEvent) -> Boolean,
    ): SentryLogEvent = buffer.await(description) { logs -> logs.firstOrNull(predicate) }
}
