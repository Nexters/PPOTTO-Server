package com.github.nexters.ppotto.auth.application

import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.LoginResult
import com.github.nexters.ppotto.auth.domain.OAuthProvider
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@ConditionalOnBean(AuthUserPort::class, AuthTermsPort::class)
class AuthService(
    oauthClients: List<OAuthClient>,
    private val tokenProvider: TokenProvider,
    private val refreshTokenStore: RefreshTokenStore,
    private val authUserPort: AuthUserPort,
    private val authTermsPort: AuthTermsPort,
) {
    private val oauthClients =
        oauthClients.associateBy(OAuthClient::provider).also {
            check(it.size == oauthClients.size) { "OAuth provider별 client는 하나만 등록할 수 있습니다." }
        }

    @Transactional
    fun login(command: LoginCommand): LoginResult =
        oauthClient(command.provider)
            .authenticate(command)
            .let { profile ->
                authUserPort
                    .findOrCreate(profile)
                    .also {
                        if (profile.authorizationCodeExchangeFailed && it.isNewUser) {
                            throw UnauthorizedException(AuthErrorCode.APPLE_CODE_EXCHANGE_FAILED)
                        }
                    }
            }.let { user ->
                tokenProvider
                    .issue(user.userId)
                    .also { refreshTokenStore.save(user.userId, it.refreshToken) }
                    .let {
                        LoginResult(
                            tokenPair = it,
                            isNewUser = user.isNewUser,
                            pendingTerms = authTermsPort.findPendingTerms(user.userId),
                        )
                    }
            }

    fun refresh(refreshToken: String): TokenPair =
        refreshTokenStore
            .findUserId(refreshToken)
            ?.let { userId ->
                tokenProvider
                    .issue(userId)
                    .also {
                        if (!refreshTokenStore.rotate(userId, refreshToken, it.refreshToken)) {
                            throw UnauthorizedException(AuthErrorCode.INVALID_REFRESH_TOKEN)
                        }
                    }
            } ?: throw UnauthorizedException(AuthErrorCode.INVALID_REFRESH_TOKEN)

    fun logout(userId: UUID) = refreshTokenStore.delete(userId)

    fun revokeProviderToken(
        provider: OAuthProvider,
        providerRefreshToken: String,
    ) = oauthClient(provider).revoke(providerRefreshToken)

    private fun oauthClient(provider: OAuthProvider): OAuthClient = oauthClients[provider] ?: throw InvalidInputException()
}
