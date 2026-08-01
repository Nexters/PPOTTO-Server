package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.WithdrawnUserDataDeletionPort
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class WithdrawnUserCleanupService(
    private val userRepository: UserRepository,
    private val withdrawnUserDataDeletionPort: WithdrawnUserDataDeletionPort,
) {
    fun cleanup(
        deletedBefore: Instant,
        batchSize: Int,
    ): WithdrawnUserCleanupResult =
        batchSize
            .also { require(it in 1..MAX_BATCH_SIZE) }
            .let { userRepository.findWithdrawnBefore(deletedBefore, it) }
            .let { candidates ->
                candidates
                    .map { user ->
                        withdrawnUserDataDeletionPort.deleteAllFor(user.id)
                        check(userRepository.hardDelete(user.id))
                        user.id
                    }.let {
                        WithdrawnUserCleanupResult(
                            attempted = candidates.size,
                            deletedUserIds = it,
                        )
                    }
            }

    companion object {
        private const val MAX_BATCH_SIZE = 1_000
    }
}

data class WithdrawnUserCleanupResult(
    val attempted: Int,
    val deletedUserIds: List<UserId>,
)
