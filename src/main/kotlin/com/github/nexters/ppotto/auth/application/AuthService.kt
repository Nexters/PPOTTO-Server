package com.github.nexters.ppotto.auth.application

import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
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
@ConditionalOnBean(AuthUserPort::class, AuthTermsPort::class, AuthActiveUserPort::class)
class AuthService(
    oauthClients: List<OAuthClient>,
    private val tokenProvider: TokenProvider,
    private val refreshTokenStore: RefreshTokenStore,
    private val authUserPort: AuthUserPort,
    private val authTermsPort: AuthTermsPort,
    private val authActiveUserPort: AuthActiveUserPort,
) {
    private val oauthClients =
        oauthClients.associateBy(OAuthClient::provider).also {
            check(it.size == oauthClients.size) { "OAuth provider별 client는 하나만 등록할 수 있습니다." }
        }

    @Transactional
    fun login(command: LoginCommand): LoginResult {
        val client = oauthClients[command.provider] ?: throw InvalidInputException()
        val profile = client.authenticate(command)
        val user = authUserPort.findOrCreate(profile)
        if (profile.authorizationCodeExchangeFailed && user.isNewUser) {
            throw UnauthorizedException(AuthErrorCode.APPLE_CODE_EXCHANGE_FAILED)
        }
        val pendingTerms = authTermsPort.findPendingTerms(user.userId)
        val tokenPair = tokenProvider.issue(user.userId)
        refreshTokenStore.save(user.userId, tokenPair.refreshToken)
        return LoginResult(tokenPair, user.isNewUser, pendingTerms)
    }

    fun refresh(refreshToken: String): TokenPair {
        val userId = refreshTokenStore.findUserId(refreshToken)
        if (userId == null || !authActiveUserPort.isActive(userId)) {
            throw UnauthorizedException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }
        val newTokenPair = tokenProvider.issue(userId)
        if (!refreshTokenStore.rotate(userId, refreshToken, newTokenPair.refreshToken)) {
            throw UnauthorizedException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }
        return newTokenPair
    }

    fun logout(userId: UUID) {
        refreshTokenStore.delete(userId)
    }

    fun revokeProviderToken(
        provider: OAuthProvider,
        providerRefreshToken: String,
    ) {
        val client = oauthClients[provider] ?: throw InvalidInputException()
        client.revoke(providerRefreshToken)
    }
}
