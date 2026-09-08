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
import io.kotest.matchers.string.shouldContain
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.InetSocketAddress
import java.net.URI

class KakaoOAuthClientTest :
    BehaviorSpec({
        val server = HttpServer.create(InetSocketAddress(0), 0)
        var tokenInfoResponse = """{"id":12345,"app_id":9876}"""
        var userInfoResponse = """{"id":12345,"kakao_account":{"email":"user@kakao.com","profile":{"nickname":"뽀또"}}}"""
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
        var tokenExchangeStatus = 200
        var tokenExchangeResponse = """{"token_type":"bearer","access_token":"web-access-token","expires_in":21599}"""
        var lastTokenExchangeBody = ""
        server.createContext("/oauth/token") { exchange ->
            lastTokenExchangeBody =
                exchange.requestBody
                    .readAllBytes()
                    .decodeToString()
            val body = tokenExchangeResponse.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(tokenExchangeStatus, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        val baseUri = "http://localhost:${server.address.port}"
        val client =
            KakaoOAuthClient(
                HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(RestClient.builder().build()))
                    .build()
                    .createClient(KakaoOAuthApi::class.java),
                KakaoAuthProperties(
                    appId = 9876,
                    accessTokenInfoUri = URI("$baseUri/token"),
                    userInfoUri = URI("$baseUri/user"),
                    clientId = "rest-api-key",
                    clientSecret = "client-secret",
                    tokenUri = URI("$baseUri/oauth/token"),
                ),
            )

        afterSpec {
            server.stop(0)
        }

        Given("우리 앱에서 발급된 카카오 access token이 주어졌을 때") {
            When("카카오 사용자 정보를 검증하면") {
                Then("회원번호와 이메일과 닉네임을 반환한다") {
                    val profile = client.authenticate(LoginCommand.Kakao("valid-token"))

                    profile.providerUserId shouldBe "12345"
                    profile.email shouldBe "user@kakao.com"
                    profile.name shouldBe "뽀또"
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

        Given("웹 인가 페이지에서 받은 카카오 authorization code가 주어졌을 때") {
            tokenInfoResponse = """{"id":12345,"app_id":9876}"""
            userInfoResponse = """{"id":12345,"kakao_account":{"email":"user@kakao.com","profile":{"nickname":"뽀또"}}}"""
            tokenExchangeStatus = 200

            When("서버가 code를 토큰으로 교환해 검증하면") {
                val profile = client.authenticate(LoginCommand.KakaoWeb("web-code", "http://localhost:3000/oauth/kakao"))

                Then("앱 로그인과 같은 회원번호와 이메일과 닉네임을 반환한다") {
                    profile.providerUserId shouldBe "12345"
                    profile.email shouldBe "user@kakao.com"
                    profile.name shouldBe "뽀또"
                }

                Then("REST API 키와 client secret과 redirect URI를 form으로 보낸다") {
                    lastTokenExchangeBody shouldContain "grant_type=authorization_code"
                    lastTokenExchangeBody shouldContain "client_id=rest-api-key"
                    lastTokenExchangeBody shouldContain "client_secret=client-secret"
                    lastTokenExchangeBody shouldContain "redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Foauth%2Fkakao"
                    lastTokenExchangeBody shouldContain "code=web-code"
                }
            }
        }

        Given("만료되었거나 redirect URI가 다른 카카오 authorization code가 주어졌을 때") {
            tokenExchangeStatus = 400
            tokenExchangeResponse = """{"error":"invalid_grant","error_description":"authorization code not found"}"""

            When("서버가 code를 토큰으로 교환하면") {
                Then("AUTH-008 예외가 발생한다") {
                    val exception =
                        shouldThrow<UnauthorizedException> {
                            client.authenticate(LoginCommand.KakaoWeb("expired-code", "http://localhost:3000/oauth/kakao"))
                        }
                    exception.errorCode shouldBe AuthErrorCode.KAKAO_CODE_EXCHANGE_FAILED
                }
            }
        }

        Given("닉네임 제공에 동의하지 않은 카카오 계정이 주어졌을 때") {
            userInfoResponse = """{"id":12345,"kakao_account":{"email":"user@kakao.com"}}"""

            When("카카오 사용자 정보를 검증하면") {
                Then("AUTH-005 예외가 발생한다") {
                    val exception =
                        shouldThrow<ForbiddenException> {
                            client.authenticate(LoginCommand.Kakao("without-nickname"))
                        }
                    exception.errorCode shouldBe AuthErrorCode.KAKAO_NICKNAME_CONSENT_REQUIRED
                }
            }
        }
    })
