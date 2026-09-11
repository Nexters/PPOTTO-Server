package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.infrastructure.config.KakaoAuthProperties
import com.github.nexters.ppotto.global.error.ForbiddenException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.oauth.OAuthProvider
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

@Component
internal class KakaoOAuthClient(
    private val kakaoOAuthApi: KakaoOAuthApi,
    private val properties: KakaoAuthProperties,
) : OAuthClient {
    override val provider = OAuthProvider.KAKAO

    override fun authenticate(command: LoginCommand): SocialProfile {
        val accessToken = accessTokenOf(command)
        val tokenInfo = fetchTokenInfo(accessToken)
        val userInfo = fetchUserInfo(accessToken)
        validateIdentity(tokenInfo, userInfo)
        return SocialProfile(provider, userInfo.id.toString(), requireEmail(userInfo), requireNickname(userInfo))
    }

    override fun revoke(providerRefreshToken: String) = Unit

    private fun fetchTokenInfo(accessToken: String): KakaoTokenInfo =
        request { kakaoOAuthApi.tokenInfo(properties.accessTokenInfoUri, "$BEARER $accessToken") }

    private fun fetchUserInfo(accessToken: String): KakaoUserInfo =
        request { kakaoOAuthApi.userInfo(properties.userInfoUri, "$BEARER $accessToken") }

    private fun <T : Any> request(call: () -> T?): T {
        val response =
            try {
                call()
            } catch (e: RestClientException) {
                fail(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED, e)
            }
        return response ?: fail(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
    }

    private fun accessTokenOf(command: LoginCommand): String =
        when (command) {
            is LoginCommand.Kakao -> command.accessToken
            is LoginCommand.KakaoWeb -> exchangeAuthorizationCode(command)
            else -> throw InvalidInputException()
        }

    private fun exchangeAuthorizationCode(command: LoginCommand.KakaoWeb): String {
        val response =
            try {
                kakaoOAuthApi.exchangeToken(
                    properties.tokenUri,
                    AUTHORIZATION_CODE,
                    properties.clientId,
                    properties.clientSecret,
                    command.redirectUri,
                    command.authorizationCode,
                )
            } catch (e: RestClientException) {
                fail(AuthErrorCode.KAKAO_CODE_EXCHANGE_FAILED, e)
            }
        return response?.accessToken?.takeIf(String::isNotBlank)
            ?: fail(AuthErrorCode.KAKAO_CODE_EXCHANGE_FAILED)
    }

    private fun validateIdentity(
        tokenInfo: KakaoTokenInfo,
        userInfo: KakaoUserInfo,
    ) {
        if (tokenInfo.appId != properties.appId || tokenInfo.id != userInfo.id) {
            fail(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
        }
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

    private fun fail(
        errorCode: AuthErrorCode,
        cause: Exception? = null,
    ): Nothing = throw UnauthorizedException(errorCode, cause = cause)

    private companion object {
        const val BEARER = "Bearer"
        const val AUTHORIZATION_CODE = "authorization_code"
    }
}
