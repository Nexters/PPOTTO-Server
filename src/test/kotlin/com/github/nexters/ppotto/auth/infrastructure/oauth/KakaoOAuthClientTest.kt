package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.infrastructure.config.KakaoAuthProperties
import com.github.nexters.ppotto.global.error.ForbiddenException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.concurrent.atomic.AtomicReference

private const val DEFAULT_TOKEN_INFO = """{"id":12345,"app_id":9876}"""
private const val DEFAULT_USER_INFO =
    """{"id":12345,"kakao_account":{"email":"user@kakao.com","profile":{"nickname":"뽀또"}}}"""
private const val DEFAULT_TOKEN_EXCHANGE =
    """{"token_type":"bearer","access_token":"web-access-token","expires_in":21599}"""
private const val WEB_REDIRECT_URI = "http://localhost:3000/oauth/kakao"

private data class KakaoStub(
    val tokenInfoStatus: Int = 200,
    val tokenInfo: String = DEFAULT_TOKEN_INFO,
    val userInfoStatus: Int = 200,
    val userInfo: String = DEFAULT_USER_INFO,
    val exchangeStatus: Int = 200,
    val exchange: String = DEFAULT_TOKEN_EXCHANGE,
)

class KakaoOAuthClientTest :
    BehaviorSpec({
        val stub = AtomicReference(KakaoStub())
        val lastExchangeBody = AtomicReference("")

        val server =
            stubOAuthServer {
                route("/token") { it.respond(stub.get().tokenInfoStatus, stub.get().tokenInfo) }
                route("/user") { it.respond(stub.get().userInfoStatus, stub.get().userInfo) }
                route("/oauth/token") { exchange ->
                    lastExchangeBody.set(
                        exchange.requestBody
                            .readAllBytes()
                            .decodeToString(),
                    )
                    exchange.respond(stub.get().exchangeStatus, stub.get().exchange)
                }
            }

        val client =
            KakaoOAuthClient(
                server.api(KakaoOAuthApi::class.java),
                KakaoAuthProperties(
                    appId = 9876,
                    accessTokenInfoUri = server.uri("/token"),
                    userInfoUri = server.uri("/user"),
                    clientId = "rest-api-key",
                    clientSecret = "client-secret",
                    tokenUri = server.uri("/oauth/token"),
                ),
            )

        beforeTest { testCase ->
            if (testCase.parent == null) {
                stub.set(KakaoStub())
                lastExchangeBody.set("")
            }
        }

        Given("우리 앱에서 발급된 카카오 access token이 주어졌을 때") {
            When("카카오 사용자 정보를 검증하면") {
                val profile = client.authenticate(LoginCommand.Kakao("valid-token"))

                Then("회원번호와 이메일과 닉네임을 반환한다") {
                    profile.providerUserId shouldBe "12345"
                    profile.email shouldBe "user@kakao.com"
                    profile.name shouldBe "뽀또"
                }
            }
        }

        Given("다른 앱에서 발급된 카카오 access token이 주어졌을 때") {
            stub.updateAndGet { it.copy(tokenInfo = """{"id":12345,"app_id":1111}""") }

            When("카카오 사용자 정보를 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(LoginCommand.Kakao("substituted-token"))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("token 소유자와 사용자 정보의 회원번호가 다를 때") {
            stub.updateAndGet {
                it.copy(userInfo = """{"id":99999,"kakao_account":{"email":"other@kakao.com","profile":{"nickname":"남"}}}""")
            }

            When("카카오 사용자 정보를 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(LoginCommand.Kakao("mismatched-token"))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("만료된 access token이라 카카오가 401을 반환할 때") {
            stub.updateAndGet {
                it.copy(userInfoStatus = 401, userInfo = """{"msg":"this access token does not exist","code":-401}""")
            }

            When("카카오 사용자 정보를 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(LoginCommand.Kakao("expired-token"))
                    }

                Then("AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("카카오가 200으로 빈 본문을 반환할 때") {
            stub.updateAndGet { it.copy(userInfo = "") }

            When("카카오 사용자 정보를 검증하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(LoginCommand.Kakao("empty-body-token"))
                    }

                Then("NPE 대신 AUTH-001 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("이메일 제공에 동의하지 않은 카카오 계정이 주어졌을 때") {
            stub.updateAndGet { it.copy(userInfo = """{"id":12345,"kakao_account":{}}""") }

            When("카카오 사용자 정보를 검증하면") {
                val exception =
                    shouldThrow<ForbiddenException> {
                        client.authenticate(LoginCommand.Kakao("without-email"))
                    }

                Then("AUTH-004 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.KAKAO_EMAIL_CONSENT_REQUIRED
                }
            }
        }

        Given("닉네임 제공에 동의하지 않은 카카오 계정이 주어졌을 때") {
            stub.updateAndGet { it.copy(userInfo = """{"id":12345,"kakao_account":{"email":"user@kakao.com"}}""") }

            When("카카오 사용자 정보를 검증하면") {
                val exception =
                    shouldThrow<ForbiddenException> {
                        client.authenticate(LoginCommand.Kakao("without-nickname"))
                    }

                Then("AUTH-005 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.KAKAO_NICKNAME_CONSENT_REQUIRED
                }
            }
        }

        Given("웹 인가 페이지에서 받은 카카오 authorization code가 주어졌을 때") {
            When("서버가 code를 토큰으로 교환해 검증하면") {
                val profile = client.authenticate(LoginCommand.KakaoWeb("web-code", WEB_REDIRECT_URI))

                Then("앱 로그인과 같은 회원번호와 이메일과 닉네임을 반환한다") {
                    profile.providerUserId shouldBe "12345"
                    profile.email shouldBe "user@kakao.com"
                    profile.name shouldBe "뽀또"
                }

                Then("REST API 키와 client secret과 redirect URI를 form으로 보낸다") {
                    lastExchangeBody.get() shouldContain "grant_type=authorization_code"
                    lastExchangeBody.get() shouldContain "client_id=rest-api-key"
                    lastExchangeBody.get() shouldContain "client_secret=client-secret"
                    lastExchangeBody.get() shouldContain "redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Foauth%2Fkakao"
                    lastExchangeBody.get() shouldContain "code=web-code"
                }
            }
        }

        Given("만료되었거나 redirect URI가 다른 카카오 authorization code가 주어졌을 때") {
            stub.updateAndGet {
                it.copy(
                    exchangeStatus = 400,
                    exchange = """{"error":"invalid_grant","error_description":"authorization code not found"}""",
                )
            }

            When("서버가 code를 토큰으로 교환하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(LoginCommand.KakaoWeb("expired-code", WEB_REDIRECT_URI))
                    }

                Then("AUTH-008 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.KAKAO_CODE_EXCHANGE_FAILED
                }
            }
        }

        Given("카카오 token 교환이 200인데 access_token을 담지 않았을 때") {
            stub.updateAndGet { it.copy(exchange = """{"token_type":"bearer","expires_in":21599}""") }

            When("서버가 code를 토큰으로 교환하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(LoginCommand.KakaoWeb("no-token-code", WEB_REDIRECT_URI))
                    }

                Then("AUTH-008 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.KAKAO_CODE_EXCHANGE_FAILED
                }
            }
        }

        Given("카카오 token 교환이 200인데 access_token이 공백일 때") {
            stub.updateAndGet { it.copy(exchange = """{"token_type":"bearer","access_token":"   "}""") }

            When("서버가 code를 토큰으로 교환하면") {
                val exception =
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(LoginCommand.KakaoWeb("blank-token-code", WEB_REDIRECT_URI))
                    }

                Then("AUTH-008 예외가 발생한다") {
                    exception.errorCode shouldBe AuthErrorCode.KAKAO_CODE_EXCHANGE_FAILED
                }
            }
        }
    })
