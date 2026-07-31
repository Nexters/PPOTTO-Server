package com.github.nexters.ppotto.auth.application

import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.AuthSignup
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.LoginResult
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.util.UUID

@Service
@ConditionalOnBean(AuthUserPort::class, AuthTermsPort::class, AuthActiveUserPort::class)
class AuthService(
    oauthClients: List<OAuthClient>,
    @Qualifier(SIGNUP_TRANSACTION)
    private val signupTransaction: TransactionOperations,
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

    fun login(command: LoginCommand): LoginResult =
        (oauthClients[command.provider] ?: throw InvalidInputException())
            .authenticate(command)
            .let(::signUp)
            .let { signup ->
                tokenProvider
                    .issue(signup.user.userId)
                    .also { refreshTokenStore.save(signup.user.userId, it.refreshToken) }
                    .let { LoginResult(it, signup.user.isNewUser, signup.pendingTerms) }
            }

    fun refresh(refreshToken: String): TokenPair =
        refreshTokenStore
            .findUserId(refreshToken)
            ?.takeIf(authActiveUserPort::isActive)
            ?.let { userId ->
                tokenProvider
                    .issue(userId)
                    .takeIf { refreshTokenStore.rotate(userId, refreshToken, it.refreshToken) }
            } ?: throw UnauthorizedException(AuthErrorCode.INVALID_REFRESH_TOKEN)

    fun logout(userId: UUID) = refreshTokenStore.delete(userId)

    private fun signUp(profile: SocialProfile): AuthSignup =
        signupTransaction.execute {
            authUserPort
                .findOrCreate(profile)
                .takeUnless { profile.authorizationCodeExchangeFailed && it.isNewUser }
                ?.let { AuthSignup(it, authTermsPort.findPendingTerms(it.userId)) }
                ?: throw UnauthorizedException(AuthErrorCode.APPLE_CODE_EXCHANGE_FAILED)
        }

    companion object {
        const val SIGNUP_TRANSACTION = "signupTransaction"
    }
}
