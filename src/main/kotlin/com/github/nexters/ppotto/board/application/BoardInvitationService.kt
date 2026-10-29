package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardInvitationIssueResult
import com.github.nexters.ppotto.board.application.model.BoardInvitationPreviewImage
import com.github.nexters.ppotto.board.application.model.BoardInvitationPreviewUploadResult
import com.github.nexters.ppotto.board.application.port.BoardInvitationPreviewStorage
import com.github.nexters.ppotto.board.domain.BoardInvitation
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardInvitationRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.logging.bestEffort
import com.github.nexters.ppotto.global.retry.RetryPolicy
import com.github.nexters.ppotto.global.retry.retrying
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Locale

@Service
class BoardInvitationService(
    private val boardAccessService: BoardAccessService,
    private val boardInvitationRepository: BoardInvitationRepository,
    private val previewStorage: BoardInvitationPreviewStorage,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    fun issue(
        boardId: BoardId,
        userId: UserId,
    ): BoardInvitationIssueResult {
        val outcome =
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
        outcome.expiredPreviewImageKey?.let { objectKey ->
            bestEffort(log, "만료된 보드 초대 미리보기 정리(objectKey=$objectKey)") {
                previewStorage.delete(objectKey)
            }
        }
        return outcome.result
    }

    fun uploadPreview(
        invitationId: BoardInvitationId,
        userId: UserId,
        code: String,
        image: BoardInvitationPreviewImage,
    ): BoardInvitationPreviewUploadResult {
        validateUploadTarget(boardInvitationRepository.findById(invitationId), userId, code)
        var uploadedObjectKey: String? = null
        try {
            val objectKey = previewStorage.upload(invitationId, image.bytes)
            uploadedObjectKey = objectKey
            val previewImageUrl = previewStorage.issueReadUrl(objectKey)
            val previousObjectKey =
                checkNotNull(
                    transactionTemplate.execute {
                        val invitation = boardInvitationRepository.findByIdForUpdate(invitationId)
                        validateUploadTarget(invitation, userId, code)
                        boardInvitationRepository.updatePreviewImageKey(invitationId, objectKey)
                        BoardInvitationPreviewReplacement(invitation?.previewImageKey)
                    },
                ).previousObjectKey
            previousObjectKey?.let { previous ->
                bestEffort(log, "교체된 보드 초대 미리보기 정리(objectKey=$previous)") {
                    previewStorage.delete(previous)
                }
            }
            return BoardInvitationPreviewUploadResult(previewImageUrl)
        } catch (failure: Exception) {
            uploadedObjectKey?.let { objectKey ->
                bestEffort(log, "반영되지 않은 보드 초대 미리보기 정리(objectKey=$objectKey)") {
                    previewStorage.delete(objectKey)
                }
            }
            throw failure
        }
    }

    private fun issueInTransaction(
        boardId: BoardId,
        userId: UserId,
    ): BoardInvitationIssueOutcome {
        boardAccessService.getReadableByIdForUpdate(boardId, userId)
        val now = Instant.now()
        val existing = boardInvitationRepository.findByBoardIdAndInviterUserIdForUpdate(boardId, userId)
        if (existing != null && existing.expiresAt.isAfter(now)) {
            return BoardInvitationIssueOutcome(existing.toIssueResult(), expiredPreviewImageKey = null)
        }

        val code = generateCode()
        val expiresAt = now.plus(INVITATION_VALIDITY)
        val invitation =
            if (existing == null) {
                boardInvitationRepository.save(boardId, userId, code, expiresAt)
            } else {
                boardInvitationRepository.reissue(existing.id, code, expiresAt)
            }
        return BoardInvitationIssueOutcome(invitation.toIssueResult(), existing?.previewImageKey)
    }

    private fun validateUploadTarget(
        invitation: BoardInvitation?,
        userId: UserId,
        code: String,
    ) {
        if (invitation == null || invitation.inviterUserId != userId || !invitation.expiresAt.isAfter(Instant.now())) {
            throw NotFoundException(BoardErrorCode.INVITATION_NOT_FOUND)
        }
        if (invitation.code != code.uppercase(Locale.ROOT)) {
            throw ConflictException(BoardErrorCode.INVITATION_CODE_RENEWED)
        }
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

private data class BoardInvitationIssueOutcome(
    val result: BoardInvitationIssueResult,
    val expiredPreviewImageKey: String?,
)

private data class BoardInvitationPreviewReplacement(
    val previousObjectKey: String?,
)
