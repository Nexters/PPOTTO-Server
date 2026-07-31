package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.user.application.port.ProviderRefreshTokenCipher
import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import com.github.nexters.ppotto.user.application.port.UserSessionRevoker
import com.github.nexters.ppotto.user.domain.User
import com.github.nexters.ppotto.user.domain.UserErrorCode
import com.github.nexters.ppotto.user.infrastructure.SocialUserRepository
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val socialUserRepository: SocialUserRepository,
    private val tokenCipher: ProviderRefreshTokenCipher,
    private val socialAccountRevoker: SocialAccountRevoker,
    private val userSessionRevoker: UserSessionRevoker,
) {
    @Transactional
    fun findOrCreate(command: SocialUserCommand): UserRegistrationResult {
        val encryptedToken = command.providerRefreshToken?.let(tokenCipher::encrypt)
        val created =
            socialUserRepository.saveIfAbsent(
                provider = command.provider,
                providerUserId = command.providerUserId,
                email = command.email,
                providerRefreshToken = encryptedToken,
            )
        if (created != null) {
            return UserRegistrationResult(created, true)
        }

        val existing =
            userRepository.findBySocialAccount(command.provider, command.providerUserId)
                ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)
        val updated =
            userRepository.updateSocialProfile(
                id = existing.id,
                email = command.email,
                providerRefreshToken = encryptedToken,
            ) ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)
        return UserRegistrationResult(updated, false)
    }

    @Transactional(readOnly = true)
    fun getById(id: UUID): User = userRepository.findById(id) ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)

    @Transactional(readOnly = true)
    fun isActive(id: UUID): Boolean = userRepository.findById(id) != null

    @Transactional
    fun withdraw(
        id: UUID,
        withdrawnAt: Instant = Instant.now(),
    ) {
        val user = getById(id)
        user.providerRefreshToken?.let {
            socialAccountRevoker.revoke(user.provider, tokenCipher.decrypt(it))
        }
        userRepository.withdraw(user.withdraw(withdrawnAt))
            ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)
        userSessionRevoker.revoke(id)
    }
}
