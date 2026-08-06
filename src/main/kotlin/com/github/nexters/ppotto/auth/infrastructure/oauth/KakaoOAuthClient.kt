package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.config.KakaoAuthProperties
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.global.error.ForbiddenException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

@Component
internal class KakaoOAuthClient(
    private val kakaoOAuthApi: KakaoOAuthApi,
    private val properties: KakaoAuthProperties,
) : OAuthClient {
    override val provider = OAuthProvider.KAKAO

    override fun authenticate(command: LoginCommand): SocialProfile =
        requireKakaoCommand(command).let { kakaoCommand ->
            fetchTokenInfo(kakaoCommand.accessToken).let { tokenInfo ->
                fetchUserInfo(kakaoCommand.accessToken)
                    .also { validateIdentity(tokenInfo, it) }
                    .let { SocialProfile(provider, it.id.toString(), requireEmail(it), requireNickname(it)) }
            }
        }

    override fun revoke(providerRefreshToken: String) = Unit

    private fun fetchTokenInfo(accessToken: String): KakaoTokenInfo =
        request { kakaoOAuthApi.tokenInfo(properties.accessTokenInfoUri, bearer(accessToken)) }

    private fun fetchUserInfo(accessToken: String): KakaoUserInfo =
        request { kakaoOAuthApi.userInfo(properties.userInfoUri, bearer(accessToken)) }

    private fun <T : Any> request(call: () -> T?): T =
        try {
            call() ?: throw UnauthorizedException(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
        } catch (e: UnauthorizedException) {
            throw e
        } catch (e: RestClientException) {
            failAuthentication(e)
        }

    private fun bearer(accessToken: String): String = "$BEARER $accessToken"

    private fun requireKakaoCommand(command: LoginCommand): LoginCommand.Kakao =
        command as? LoginCommand.Kakao ?: throw InvalidInputException()

    private fun validateIdentity(
        tokenInfo: KakaoTokenInfo,
        userInfo: KakaoUserInfo,
    ) {
        tokenInfo
            .takeIf { it.appId == properties.appId && it.id == userInfo.id }
            ?: failAuthentication()
    }

    private fun requireEmail(userInfo: KakaoUserInfo): String =
        userInfo.account
            ?.email
            ?.takeIf(String::isNotBlank)
            ?: throw ForbiddenException(AuthErrorCode.KAKAO_EMAIL_CONSENT_REQUIRED)

    private fun requireNickname(userInfo: KakaoUserInfo): String =
        userInfo.account
            ?.profile
            ?.nickname
            ?.takeIf(String::isNotBlank)
            ?: throw ForbiddenException(AuthErrorCode.KAKAO_NICKNAME_CONSENT_REQUIRED)

    private fun failAuthentication(cause: Exception? = null): Nothing =
        UnauthorizedException(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
            .also { exception -> cause?.let(exception::addSuppressed) }
            .let { throw it }

    private companion object {
        const val BEARER = "Bearer"
    }
}
