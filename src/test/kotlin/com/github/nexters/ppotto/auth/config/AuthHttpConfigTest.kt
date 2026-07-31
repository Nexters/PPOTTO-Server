package com.github.nexters.ppotto.auth.config

import com.github.nexters.ppotto.auth.infrastructure.oauth.AppleOAuthHttpService
import com.github.nexters.ppotto.auth.infrastructure.oauth.KakaoOAuthHttpService
import com.github.nexters.ppotto.support.IntegrationTest
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.core.env.Environment
import org.springframework.web.service.registry.ImportHttpServices
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class AuthHttpConfigTest(
    kakaoHttpService: KakaoOAuthHttpService,
    appleHttpService: AppleOAuthHttpService,
    environment: Environment,
) : IntegrationTest({
        val authorization = AtomicReference("")
        val appleTokenBody = AtomicReference("")
        val server =
            HttpServer
                .create(InetSocketAddress(0), 0)
                .also {
                    it.createContext("/kakao-token") { exchange ->
                        authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                        exchange.respond("""{"id":12345,"app_id":9876}""")
                    }
                    it.createContext("/apple-token") { exchange ->
                        appleTokenBody.set(
                            exchange.requestBody
                                .bufferedReader()
                                .readText(),
                        )
                        exchange.respond("""{"refresh_token":"apple-refresh-token"}""")
                    }
                    it.start()
                }
        val baseUri = "http://localhost:${server.address.port}"

        afterSpec {
            server.stop(0)
        }

        Given("OAuth HTTP Service Client group이 등록된 상태에서") {
            When("등록 설정을 조회하면") {
                Then("provider별 interface가 전용 group에 연결되어 있다") {
                    val registrations =
                        AuthHttpConfig::class.java
                            .getAnnotationsByType(ImportHttpServices::class.java)

                    registrations.map(ImportHttpServices::group) shouldContainExactlyInAnyOrder
                        listOf("kakao-oauth", "apple-oauth")
                    registrations
                        .single { it.group == "kakao-oauth" }
                        .types
                        .single() shouldBe KakaoOAuthHttpService::class
                    registrations
                        .single { it.group == "apple-oauth" }
                        .types
                        .single() shouldBe AppleOAuthHttpService::class
                    environment.getProperty(
                        "spring.http.serviceclient.kakao-oauth.connect-timeout",
                        Duration::class.java,
                    ) shouldBe Duration.ofSeconds(3)
                    environment.getProperty(
                        "spring.http.serviceclient.kakao-oauth.read-timeout",
                        Duration::class.java,
                    ) shouldBe Duration.ofSeconds(5)
                }
            }

            When("카카오 응답을 조회하면") {
                Then("동적 URI와 인증 헤더를 사용해 응답을 역직렬화한다") {
                    val response =
                        kakaoHttpService.getTokenInfo(
                            URI("$baseUri/kakao-token"),
                            "Bearer access-token",
                        )

                    response?.id shouldBe 12345
                    response?.appId shouldBe 9876
                    authorization.get() shouldBe "Bearer access-token"
                }
            }

            When("애플 authorization code를 교환하면") {
                Then("form 요청을 전송하고 refresh token을 역직렬화한다") {
                    val response =
                        appleHttpService.exchangeAuthorizationCode(
                            URI("$baseUri/apple-token"),
                            "client-id",
                            "client-secret",
                            "authorization-code",
                            "authorization_code",
                        )

                    response?.refreshToken shouldBe "apple-refresh-token"
                    appleTokenBody.get().contains("code=authorization-code") shouldBe true
                }
            }
        }
    }) {
    companion object {
        private fun HttpExchange.respond(body: String) {
            responseHeaders.add("Content-Type", "application/json")
            body
                .toByteArray()
                .also { sendResponseHeaders(200, it.size.toLong()) }
                .let { responseBody.use { output -> output.write(it) } }
        }
    }
}
