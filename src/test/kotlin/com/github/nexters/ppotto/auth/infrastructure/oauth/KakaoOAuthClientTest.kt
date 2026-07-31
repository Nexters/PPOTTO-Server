package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.config.KakaoAuthProperties
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.global.error.ForbiddenException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.net.URI

class KakaoOAuthClientTest :
    BehaviorSpec({
        var tokenInfo = KakaoTokenInfo(id = 12345, appId = 9876)
        var userInfo = KakaoUserInfo(id = 12345, account = KakaoAccount("user@kakao.com"))
        var requestedAuthorization = ""
        val httpService =
            object : KakaoOAuthHttpService {
                override fun getTokenInfo(
                    uri: URI,
                    authorization: String,
                ): KakaoTokenInfo {
                    requestedAuthorization = authorization
                    return tokenInfo
                }

                override fun getUserInfo(
                    uri: URI,
                    authorization: String,
                ): KakaoUserInfo {
                    requestedAuthorization = authorization
                    return userInfo
                }
            }
        val client =
            KakaoOAuthClient(
                httpService,
                KakaoAuthProperties(
                    appId = 9876,
                    accessTokenInfoUri = URI("https://kapi.kakao.com/v1/user/access_token_info"),
                    userInfoUri = URI("https://kapi.kakao.com/v2/user/me"),
                ),
            )

        Given("우리 앱에서 발급된 카카오 access token이 주어졌을 때") {
            When("카카오 사용자 정보를 검증하면") {
                Then("회원번호와 이메일을 반환한다") {
                    val profile = client.authenticate(LoginCommand.Kakao("valid-token"))

                    profile.providerUserId shouldBe "12345"
                    profile.email shouldBe "user@kakao.com"
                    requestedAuthorization shouldBe "Bearer valid-token"
                }
            }
        }

        Given("다른 앱에서 발급된 카카오 access token이 주어졌을 때") {
            tokenInfo = KakaoTokenInfo(id = 12345, appId = 1111)

            When("카카오 사용자 정보를 검증하면") {
                Then("AUTH-001 예외가 발생한다") {
                    shouldThrow<UnauthorizedException> {
                        client.authenticate(LoginCommand.Kakao("substituted-token"))
                    }.errorCode shouldBe AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED
                }
            }
        }

        Given("이메일 제공에 동의하지 않은 카카오 계정이 주어졌을 때") {
            tokenInfo = KakaoTokenInfo(id = 12345, appId = 9876)
            userInfo = KakaoUserInfo(id = 12345, account = KakaoAccount(null))

            When("카카오 사용자 정보를 검증하면") {
                Then("AUTH-004 예외가 발생한다") {
                    shouldThrow<ForbiddenException> {
                        client.authenticate(LoginCommand.Kakao("without-email"))
                    }.errorCode shouldBe AuthErrorCode.KAKAO_EMAIL_CONSENT_REQUIRED
                }
            }
        }
    })
