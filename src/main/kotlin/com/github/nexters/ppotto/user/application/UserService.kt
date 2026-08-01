package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.UserId
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

@Service
class UserService(
    private val userRepository: UserRepository,
    private val socialUserRepository: SocialUserRepository,
    private val tokenCipher: ProviderRefreshTokenCipher,
    private val socialAccountRevoker: SocialAccountRevoker,
    private val userSessionRevoker: UserSessionRevoker,
) {
    @Transactional
    fun findOrCreate(command: SocialUserCommand): UserRegistrationResult =
        command.providerRefreshToken
            ?.let(tokenCipher::encrypt)
            .let { encryptedToken ->
                socialUserRepository
                    .saveIfAbsent(
                        provider = command.provider,
                        providerUserId = command.providerUserId,
                        email = command.email,
                        providerRefreshToken = encryptedToken,
                    )?.let { UserRegistrationResult(it, true) }
                    ?: userRepository
                        .findBySocialAccount(command.provider, command.providerUserId)
                        ?.let {
                            userRepository.updateSocialProfile(
                                id = it.id,
                                email = command.email,
                                providerRefreshToken = encryptedToken,
                            )
                        }?.let { UserRegistrationResult(it, false) }
                    ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)
            }

    @Transactional(readOnly = true)
    fun getById(id: UserId): User = userRepository.findById(id) ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)

    @Transactional(readOnly = true)
    fun isActive(id: UserId): Boolean = userRepository.findById(id) != null

    @Transactional
    fun withdraw(
        id: UserId,
        withdrawnAt: Instant = Instant.now(),
    ): Unit =
        getById(id)
            .also { user ->
                user.providerRefreshToken?.let {
                    socialAccountRevoker.revoke(user.provider, tokenCipher.decrypt(it))
                }
            }.let { userRepository.withdraw(it.withdraw(withdrawnAt)) }
            ?.let { userSessionRevoker.revoke(id) }
            ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)
}
