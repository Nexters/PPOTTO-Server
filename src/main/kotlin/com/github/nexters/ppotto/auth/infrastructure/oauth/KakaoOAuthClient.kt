package com.github.nexters.ppotto.auth.infrastructure.oauth

import com.fasterxml.jackson.annotation.JsonProperty
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
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class KakaoOAuthClient(
    restClientBuilder: RestClient.Builder,
    private val properties: KakaoAuthProperties,
) : OAuthClient {
    override val provider = OAuthProvider.KAKAO
    private val restClient = restClientBuilder.build()

    override fun authenticate(command: LoginCommand): SocialProfile =
        requireKakaoCommand(command).let { kakaoCommand ->
            fetchTokenInfo(kakaoCommand.accessToken).let { tokenInfo ->
                fetchUserInfo(kakaoCommand.accessToken)
                    .also { validateIdentity(tokenInfo, it) }
                    .let { SocialProfile(provider, it.id.toString(), requireEmail(it)) }
            }
        }

    override fun revoke(providerRefreshToken: String) = Unit

    private fun fetchTokenInfo(accessToken: String): KakaoTokenInfo =
        request(accessToken, properties.accessTokenInfoUri, KakaoTokenInfo::class.java)

    private fun fetchUserInfo(accessToken: String): KakaoUserInfo = request(accessToken, properties.userInfoUri, KakaoUserInfo::class.java)

    private fun <T : Any> request(
        accessToken: String,
        uri: java.net.URI,
        responseType: Class<T>,
    ): T =
        try {
            restClient
                .get()
                .uri(uri)
                .header(AUTHORIZATION, "$BEARER $accessToken")
                .retrieve()
                .body(responseType)
                ?: throw UnauthorizedException(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
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

    private fun failAuthentication(cause: Exception? = null): Nothing =
        UnauthorizedException(AuthErrorCode.SOCIAL_AUTHENTICATION_FAILED)
            .also { exception -> cause?.let(exception::addSuppressed) }
            .let { throw it }

    private data class KakaoTokenInfo(
        val id: Long,
        @JsonProperty("app_id")
        val appId: Long,
    )

    private data class KakaoUserInfo(
        val id: Long,
        @JsonProperty("kakao_account")
        val account: KakaoAccount?,
    )

    private data class KakaoAccount(
        val email: String?,
    )

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer"
    }
}
