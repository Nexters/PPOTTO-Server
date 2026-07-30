package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.user.application.port.ProviderRefreshTokenCipher
import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import com.github.nexters.ppotto.user.domain.EncryptedProviderRefreshToken
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
) {
    @Transactional
    fun findOrCreate(command: SocialUserCommand): UserRegistrationResult =
        command.run {
            val encryptedToken = providerRefreshToken?.let(tokenCipher::encrypt)
            socialUserRepository
                .saveIfAbsent(
                    provider = provider,
                    providerUserId = providerUserId,
                    email = email,
                    providerRefreshToken = encryptedToken,
                )?.let { UserRegistrationResult(it, true) }
                ?: updateExisting(command, encryptedToken)
        }

    private fun updateExisting(
        command: SocialUserCommand,
        encryptedToken: EncryptedProviderRefreshToken?,
    ): UserRegistrationResult =
        userRepository
            .findBySocialAccount(command.provider, command.providerUserId)
            ?.let { existing ->
                userRepository.updateSocialProfile(
                    id = existing.id,
                    email = command.email,
                    providerRefreshToken = encryptedToken,
                )
            }?.let { UserRegistrationResult(it, false) }
            ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)

    @Transactional(readOnly = true)
    fun getById(id: UUID): User =
        userRepository
            .findById(id)
            ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)

    @Transactional
    fun withdraw(
        id: UUID,
        withdrawnAt: Instant = Instant.now(),
    ) {
        getById(id)
            .also { user ->
                user.providerRefreshToken?.let {
                    socialAccountRevoker.revoke(user.provider, tokenCipher.decrypt(it))
                }
            }.withdraw(withdrawnAt)
            .let(userRepository::withdraw)
            ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)
    }
}
