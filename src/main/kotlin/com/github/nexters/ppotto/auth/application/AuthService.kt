package com.github.nexters.ppotto.auth.application

import com.github.nexters.ppotto.auth.application.port.AuthActiveUserPort
import com.github.nexters.ppotto.auth.application.port.AuthTermsPort
import com.github.nexters.ppotto.auth.application.port.AuthUserPort
import com.github.nexters.ppotto.auth.application.port.OAuthClient
import com.github.nexters.ppotto.auth.application.port.RefreshTokenStore
import com.github.nexters.ppotto.auth.application.port.TokenProvider
import com.github.nexters.ppotto.auth.application.port.byProvider
import com.github.nexters.ppotto.auth.domain.AuthErrorCode
import com.github.nexters.ppotto.auth.domain.AuthSignup
import com.github.nexters.ppotto.auth.domain.LoginCommand
import com.github.nexters.ppotto.auth.domain.LoginResult
import com.github.nexters.ppotto.auth.domain.SocialProfile
import com.github.nexters.ppotto.auth.domain.TokenPair
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations

@Service
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
    private val log = LoggerFactory.getLogger(javaClass)
    private val oauthClients = oauthClients.byProvider()

    fun login(command: LoginCommand): LoginResult {
        val signup =
            checkNotNull(oauthClients[command.provider]) { "OAuth provider client가 연결되지 않았습니다." }
                .authenticate(command)
                .let(::signUp)
        val tokenPair = tokenProvider.issue(signup.user.userId)
        refreshTokenStore.save(signup.user.userId, tokenPair.refreshToken)
        return LoginResult(tokenPair, signup.user.isNewUser, signup.pendingTerms)
    }

    fun refresh(refreshToken: String): TokenPair {
        val userId = refreshTokenStore.findUserId(refreshToken) ?: failRefresh(UNKNOWN_REFRESH_TOKEN)
        if (!authActiveUserPort.isActive(userId)) {
            failRefresh(INACTIVE_USER)
        }
        val tokenPair = tokenProvider.issue(userId)
        if (!refreshTokenStore.rotate(userId, refreshToken, tokenPair.refreshToken)) {
            failRefresh(ROTATION_REJECTED)
        }
        return tokenPair
    }

    fun logout(userId: UserId) = refreshTokenStore.delete(userId)

    private fun signUp(profile: SocialProfile): AuthSignup =
        signupTransaction.execute {
            val user = authUserPort.findOrCreate(profile) ?: throw signupRequirementFailure(profile)
            if (profile.authorizationCodeExchangeFailed && user.isNewUser) {
                throw UnauthorizedException(AuthErrorCode.APPLE_CODE_EXCHANGE_FAILED)
            }
            AuthSignup(user, authTermsPort.findPendingTerms(user.userId))
        }

    private fun signupRequirementFailure(profile: SocialProfile): InvalidInputException =
        InvalidInputException(
            if (profile.email == null) AuthErrorCode.SIGNUP_EMAIL_REQUIRED else AuthErrorCode.SIGNUP_NAME_REQUIRED,
        )

    private fun failRefresh(reason: String): Nothing {
        log.info("refresh token 재발급에 실패했습니다. reason={}", reason)
        throw UnauthorizedException(AuthErrorCode.INVALID_REFRESH_TOKEN)
    }

    companion object {
        const val SIGNUP_TRANSACTION = "signupTransaction"
        private const val UNKNOWN_REFRESH_TOKEN = "unknown_refresh_token"
        private const val INACTIVE_USER = "inactive_user"
        private const val ROTATION_REJECTED = "rotation_rejected"
    }
}
