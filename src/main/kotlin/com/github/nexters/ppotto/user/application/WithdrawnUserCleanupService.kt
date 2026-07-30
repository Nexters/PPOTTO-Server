package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.user.application.port.WithdrawnUserDataDeletionPort
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class WithdrawnUserCleanupService(
    private val userRepository: UserRepository,
    private val withdrawnUserDataDeletionPort: WithdrawnUserDataDeletionPort,
) {
    fun cleanup(
        deletedBefore: Instant,
        batchSize: Int,
    ): WithdrawnUserCleanupResult {
        require(batchSize in 1..MAX_BATCH_SIZE)
        return userRepository
            .findWithdrawnBefore(deletedBefore, batchSize)
            .let { candidates ->
                WithdrawnUserCleanupResult(
                    attempted = candidates.size,
                    deletedUserIds =
                        candidates.map { user ->
                            user.id
                                .also(withdrawnUserDataDeletionPort::deleteAllFor)
                                .also { check(userRepository.hardDelete(it)) }
                        },
                )
            }
    }

    companion object {
        private const val MAX_BATCH_SIZE = 1_000
    }
}

data class WithdrawnUserCleanupResult(
    val attempted: Int,
    val deletedUserIds: List<UUID>,
)
