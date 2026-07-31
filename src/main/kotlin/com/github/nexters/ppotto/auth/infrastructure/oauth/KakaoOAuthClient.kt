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
class KakaoOAuthClient(
    private val httpService: KakaoOAuthHttpService,
    private val properties: KakaoAuthProperties,
) : OAuthClient {
    override val provider = OAuthProvider.KAKAO

    override fun authenticate(command: LoginCommand): SocialProfile =
        requireKakaoCommand(command).run {
            val tokenInfo = fetchTokenInfo(accessToken)
            val userInfo = fetchUserInfo(accessToken)
            validateIdentity(tokenInfo, userInfo)
            SocialProfile(provider, userInfo.id.toString(), requireEmail(userInfo))
        }

    override fun revoke(providerRefreshToken: String) = Unit

    private fun fetchTokenInfo(accessToken: String): KakaoTokenInfo =
        request { httpService.getTokenInfo(properties.accessTokenInfoUri, accessToken.asBearerToken()) }

    private fun fetchUserInfo(accessToken: String): KakaoUserInfo =
        request { httpService.getUserInfo(properties.userInfoUri, accessToken.asBearerToken()) }

    private fun <T : Any> request(block: () -> T?): T =
        try {
            block() ?: failAuthentication()
        } catch (e: UnauthorizedException) {
            throw e
        } catch (e: RestClientException) {
            failAuthentication(e)
        }

    private fun requireKakaoCommand(command: LoginCommand): LoginCommand.Kakao =
        command as? LoginCommand.Kakao ?: throw InvalidInputException()

    private fun validateIdentity(
        tokenInfo: KakaoTokenInfo,
        userInfo: KakaoUserInfo,
    ) {
        if (tokenInfo.appId != properties.appId || tokenInfo.id != userInfo.id) failAuthentication()
    }

    private fun requireEmail(userInfo: KakaoUserInfo): String =
        userInfo.account
            ?.email
            ?.takeIf(String::isNotBlank)
            ?: throw ForbiddenException(AuthErrorCode.KAKAO_EMAIL_CONSENT_REQUIRED)

    private fun String.asBearerToken(): String = "$BEARER $this"

    private fun failAuthentication(cause: Exception? = null): Nothing {
        val exception = UnauthorizedException(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
        cause?.let(exception::addSuppressed)
        throw exception
    }

    private companion object {
        const val BEARER = "Bearer"
    }
}
