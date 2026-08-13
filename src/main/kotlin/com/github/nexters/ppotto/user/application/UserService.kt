package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.ProviderRefreshTokenCipher
import com.github.nexters.ppotto.user.application.port.SocialAccountRevoker
import com.github.nexters.ppotto.user.application.port.UserSessionRevoker
import com.github.nexters.ppotto.user.domain.EncryptedProviderRefreshToken
import com.github.nexters.ppotto.user.domain.User
import com.github.nexters.ppotto.user.domain.UserErrorCode
import com.github.nexters.ppotto.user.infrastructure.SocialUserRepository
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun findOrCreate(command: SocialUserCommand): UserRegistrationResult? =
        command.providerRefreshToken
            ?.let(tokenCipher::encrypt)
            .let { encryptedToken ->
                command
                    .signupIdentity()
                    ?.let { identity ->
                        create(command, identity, encryptedToken)
                            ?: refresh(command, encryptedToken)
                            ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)
                    }
                    ?: refresh(command, encryptedToken)
            }

    private fun SocialUserCommand.signupIdentity(): SignupIdentity? =
        email?.let { signupEmail ->
            name?.let { signupName -> SignupIdentity(signupEmail, signupName) }
        }

    private fun create(
        command: SocialUserCommand,
        identity: SignupIdentity,
        encryptedToken: EncryptedProviderRefreshToken?,
    ): UserRegistrationResult? =
        socialUserRepository
            .saveIfAbsent(
                provider = command.provider,
                providerUserId = command.providerUserId,
                email = identity.email,
                name = identity.name,
                providerRefreshToken = encryptedToken,
            )?.let { UserRegistrationResult(it, true) }

    private fun refresh(
        command: SocialUserCommand,
        encryptedToken: EncryptedProviderRefreshToken?,
    ): UserRegistrationResult? =
        userRepository
            .findBySocialAccount(command.provider, command.providerUserId)
            ?.let {
                userRepository.updateSocialProfile(
                    id = it.id,
                    email = command.email,
                    providerRefreshToken = encryptedToken,
                )
            }?.let { UserRegistrationResult(it, false) }

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
                user.providerRefreshToken
                    ?.let { socialAccountRevoker.revoke(user.provider, tokenCipher.decrypt(it)) }
                    ?: log.warn(
                        "provider refresh token이 없어 소셜 계정 해지를 건너뜁니다. 재가입 시 이름을 다시 받아야 합니다. userId={}, provider={}",
                        user.id,
                        user.provider,
                    )
            }.let { userRepository.withdraw(it.withdraw(withdrawnAt)) }
            ?.let { userSessionRevoker.revoke(id) }
            ?: throw NotFoundException(UserErrorCode.USER_NOT_FOUND)

    private data class SignupIdentity(
        val email: String,
        val name: String,
    )
}
