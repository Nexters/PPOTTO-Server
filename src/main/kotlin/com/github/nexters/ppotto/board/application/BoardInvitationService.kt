package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardInvitationIssueResult
import com.github.nexters.ppotto.board.domain.BoardInvitation
import com.github.nexters.ppotto.board.infrastructure.BoardInvitationRepository
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.retry.RetryPolicy
import com.github.nexters.ppotto.global.retry.retrying
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

@Service
class BoardInvitationService(
    private val boardAccessService: BoardAccessService,
    private val boardInvitationRepository: BoardInvitationRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    fun issue(
        boardId: BoardId,
        userId: UserId,
    ): BoardInvitationIssueResult =
        retrying(
            policy = CODE_COLLISION_RETRY_POLICY,
            log = log,
            description = "보드 초대 코드 발급",
            retryOn = { it is DataIntegrityViolationException },
        ) {
            checkNotNull(
                transactionTemplate.execute {
                    issueInTransaction(boardId, userId)
                },
            )
        }.getOrThrow()

    private fun issueInTransaction(
        boardId: BoardId,
        userId: UserId,
    ): BoardInvitationIssueResult {
        boardAccessService.getReadableByIdForUpdate(boardId, userId)
        val now = Instant.now()
        val existing = boardInvitationRepository.findByBoardIdAndInviterUserId(boardId, userId)
        if (existing != null && existing.expiresAt.isAfter(now)) return existing.toIssueResult()

        val code = generateCode()
        val expiresAt = now.plus(INVITATION_VALIDITY)
        val invitation =
            if (existing == null) {
                boardInvitationRepository.save(boardId, userId, code, expiresAt)
            } else {
                boardInvitationRepository.reissue(existing.id, code, expiresAt)
            }
        return invitation.toIssueResult()
    }

    private fun generateCode(): String =
        buildString(INVITATION_CODE_LENGTH) {
            repeat(INVITATION_CODE_LENGTH) {
                append(INVITATION_CODE_ALPHABET[secureRandom.nextInt(INVITATION_CODE_ALPHABET.length)])
            }
        }

    private fun BoardInvitation.toIssueResult() =
        BoardInvitationIssueResult(
            invitationId = id,
            code = code,
            expiresAt = expiresAt,
            previewRequired = previewImageKey == null,
        )

    private companion object {
        const val INVITATION_CODE_LENGTH = 6
        const val INVITATION_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val INVITATION_VALIDITY: Duration = Duration.ofDays(30)
        val CODE_COLLISION_RETRY_POLICY = RetryPolicy(maxAttempts = 5)
    }
}
