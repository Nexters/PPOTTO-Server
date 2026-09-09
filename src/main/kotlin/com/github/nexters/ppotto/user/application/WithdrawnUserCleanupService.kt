package com.github.nexters.ppotto.user.application

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.WithdrawnUserAnalysisDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserBoardDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserStickerDeletionPort
import com.github.nexters.ppotto.user.application.port.WithdrawnUserTermAgreementDeletionPort
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

const val MAX_CLEANUP_BATCH_SIZE = 1_000L

@Service
class WithdrawnUserCleanupService(
    private val userRepository: UserRepository,
    private val boardDeletionPort: WithdrawnUserBoardDeletionPort,
    private val stickerDeletionPort: WithdrawnUserStickerDeletionPort,
    private val analysisDeletionPort: WithdrawnUserAnalysisDeletionPort,
    private val termAgreementDeletionPort: WithdrawnUserTermAgreementDeletionPort,
) {
    fun cleanup(
        deletedBefore: Instant,
        batchSize: Int,
    ): WithdrawnUserCleanupResult {
        require(batchSize >= 1 && batchSize <= MAX_CLEANUP_BATCH_SIZE) {
            "정리 배치 크기는 1 이상 $MAX_CLEANUP_BATCH_SIZE 이하여야 합니다."
        }

        val candidates = userRepository.findWithdrawnBefore(deletedBefore, batchSize)
        val deletedUserIds =
            candidates.map { user ->
                deleteAllDataOf(user.id)
                check(userRepository.hardDelete(user.id)) { "탈퇴 사용자 행을 삭제하지 못했습니다. userId=${user.id}" }
                user.id
            }
        return WithdrawnUserCleanupResult(attempted = candidates.size, deletedUserIds = deletedUserIds)
    }

    private fun deleteAllDataOf(userId: UserId) {
        val boardIds = boardDeletionPort.findAllBoardIds(userId)
        stickerDeletionPort.deleteAllByBoardIds(boardIds)
        analysisDeletionPort.deleteAllByUserId(userId)
        boardDeletionPort.deleteAllByUserId(userId)
        termAgreementDeletionPort.deleteAllByUserId(userId)
    }
}
