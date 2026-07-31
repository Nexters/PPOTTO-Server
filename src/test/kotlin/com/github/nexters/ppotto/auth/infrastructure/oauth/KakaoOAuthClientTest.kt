package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.config.KakaoAuthProperties
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.global.error.ForbiddenException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.URI

class KakaoOAuthClientTest :
    BehaviorSpec({
        val server = HttpServer.create(InetSocketAddress(0), 0)
        var tokenInfoResponse = """{"id":12345,"app_id":9876}"""
        var userInfoResponse = """{"id":12345,"kakao_account":{"email":"user@kakao.com"}}"""
        server.createContext("/token") { exchange ->
            val body = tokenInfoResponse.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/user") { exchange ->
            val body = userInfoResponse.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        val baseUri = "http://localhost:${server.address.port}"
        val client =
            KakaoOAuthClient(
                RestClient.builder(),
                KakaoAuthProperties(9876, URI("$baseUri/token"), URI("$baseUri/user")),
            )

        afterSpec {
            server.stop(0)
        }

        Given("우리 앱에서 발급된 카카오 access token이 주어졌을 때") {
            When("카카오 사용자 정보를 검증하면") {
                Then("회원번호와 이메일을 반환한다") {
                    val profile = client.authenticate(LoginCommand.Kakao("valid-token"))

                    profile.providerUserId shouldBe "12345"
                    profile.email shouldBe "user@kakao.com"
                }
            }
        }

        Given("다른 앱에서 발급된 카카오 access token이 주어졌을 때") {
            tokenInfoResponse = """{"id":12345,"app_id":1111}"""

            When("카카오 사용자 정보를 검증하면") {
                Then("AUTH-001 예외가 발생한다") {
                    val exception =
                        shouldThrow<UnauthorizedException> {
                            client.authenticate(LoginCommand.Kakao("substituted-token"))
                        }
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("이메일 제공에 동의하지 않은 카카오 계정이 주어졌을 때") {
            tokenInfoResponse = """{"id":12345,"app_id":9876}"""
            userInfoResponse = """{"id":12345,"kakao_account":{}}"""

            When("카카오 사용자 정보를 검증하면") {
                Then("AUTH-004 예외가 발생한다") {
                    val exception =
                        shouldThrow<ForbiddenException> {
                            client.authenticate(LoginCommand.Kakao("without-email"))
                        }
                    exception.errorCode shouldBe AuthErrorCode.KAKAO_EMAIL_CONSENT_REQUIRED
                }
            }
        }
    })
