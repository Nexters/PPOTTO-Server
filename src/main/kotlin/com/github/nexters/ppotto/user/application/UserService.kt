package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import com.github.nexters.ppotto.user.application.port.UserSessionRevoker
import com.github.nexters.ppotto.user.domain.EncryptedProviderRefreshToken
import com.github.nexters.ppotto.user.domain.User
import com.github.nexters.ppotto.user.domain.UserErrorCode
import com.github.nexters.ppotto.user.infrastructure.AesGcmProviderRefreshTokenCipher
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

@Service
class UserService(
    private val userRepository: UserRepository,
    private val tokenCipher: AesGcmProviderRefreshTokenCipher,
    private val socialAccountRevoker: SocialAccountRevoker,
    private val userSessionRevoker: UserSessionRevoker,
) {
    @Transactional
    fun findOrCreate(command: SocialUserCommand): UserRegistrationResult? {
        val encryptedToken = command.providerRefreshToken?.let(tokenCipher::encrypt)
        val email = command.email
        val name = command.name
        if (email == null || name == null) {
            return refresh(command, encryptedToken)
        }
        return create(command, email, name, encryptedToken)
            ?: refresh(command, encryptedToken)
            ?: throw ConflictException()
    }

    fun getById(id: UserId): User = userRepository.findById(id) ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)

    fun isActive(id: UserId): Boolean = userRepository.findById(id) != null

    fun withdraw(
        id: UserId,
        withdrawnAt: Instant = Instant.now(),
    ) {
        val user = getById(id)
        revokeSocialAccount(user)

        userRepository.withdraw(user.withdraw(withdrawnAt))
            ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)

        revokeSessionAfterCommit(id)
    }

    private fun create(
        command: SocialUserCommand,
        email: String,
        name: String,
        encryptedToken: EncryptedProviderRefreshToken?,
    ): UserRegistrationResult? {
        val created =
            userRepository.saveIfAbsent(
                provider = command.provider,
                providerUserId = command.providerUserId,
                email = email,
                name = name,
                providerRefreshToken = encryptedToken,
            ) ?: return null
        return UserRegistrationResult(created, true)
    }

    private fun refresh(
        command: SocialUserCommand,
        encryptedToken: EncryptedProviderRefreshToken?,
    ): UserRegistrationResult? {
        val existing = userRepository.findBySocialAccount(command.provider, command.providerUserId) ?: return null
        val refreshed =
            userRepository.updateSocialProfile(
                id = existing.id,
                email = command.email,
                providerRefreshToken = encryptedToken,
            ) ?: return null
        return UserRegistrationResult(refreshed, false)
    }

    private fun revokeSocialAccount(user: User) {
        val providerRefreshToken = user.providerRefreshToken
        if (providerRefreshToken == null) {
            log.warn(
                "provider refresh token이 없어 소셜 계정 해지를 건너뜁니다. 재가입 시 이름을 다시 받아야 합니다. userId={}, provider={}",
                user.id,
                user.provider,
            )
            return
        }
        socialAccountRevoker.revoke(user.provider, tokenCipher.decrypt(providerRefreshToken))
    }

    private fun revokeSessionAfterCommit(id: UserId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            userSessionRevoker.revoke(id)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    userSessionRevoker.revoke(id)
                }
            },
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserService::class.java)
    }
}
