package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardInvitationDetail
import com.github.nexters.ppotto.board.application.model.BoardInvitationViewerStatus
import com.github.nexters.ppotto.board.application.model.BoardParticipant
import com.github.nexters.ppotto.board.application.port.BoardInvitationPreviewStorage
import com.github.nexters.ppotto.board.application.port.BoardStickerSummaryPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.infrastructure.BoardInvitationRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.UserService
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Locale

@Service
class BoardInvitationQueryService(
    private val boardInvitationRepository: BoardInvitationRepository,
    private val boardRepository: BoardRepository,
    private val boardParticipantQueryService: BoardParticipantQueryService,
    private val stickerSummaryPort: BoardStickerSummaryPort,
    private val previewStorage: BoardInvitationPreviewStorage,
    private val userService: UserService,
) {
    fun get(
        code: String,
        currentUserId: UserId?,
    ): BoardInvitationDetail {
        val normalizedCode = code.uppercase(Locale.ROOT)
        val invitation =
            boardInvitationRepository
                .findByCode(normalizedCode)
                ?.takeIf { it.expiresAt.isAfter(Instant.now()) }
                ?: invitationNotFound()
        val board = boardRepository.findById(invitation.boardId) ?: invitationNotFound()
        val inviterName = userService.findActiveName(invitation.inviterUserId) ?: invitationNotFound()
        val boardParticipants = boardParticipantQueryService.get(board)
        val stickerCount =
            stickerSummaryPort
                .getByBoardIds(setOf(board.id), previewImageCount = 0)[board.id]
                ?.stickerCount
                ?: 0

        return BoardInvitationDetail(
            code = invitation.code,
            boardId = board.id,
            boardName = board.name,
            inviterName = inviterName,
            participantCount = boardParticipants.participantCount,
            maxParticipantCount = boardParticipants.maxParticipantCount,
            stickerCount = stickerCount,
            previewImageUrl = invitation.previewImageKey?.let(previewStorage::issueReadUrl),
            expiresAt = invitation.expiresAt,
            viewerStatus = currentUserId?.let { viewerStatus(it, boardParticipants.participants) },
            participants = currentUserId?.let { boardParticipants.participants },
        )
    }

    private fun viewerStatus(
        currentUserId: UserId,
        participants: List<BoardParticipant>,
    ): BoardInvitationViewerStatus =
        when (participants.firstOrNull { it.userId == currentUserId }?.role) {
            BoardRole.OWNER -> BoardInvitationViewerStatus.OWNER
            BoardRole.MEMBER -> BoardInvitationViewerStatus.MEMBER
            null -> BoardInvitationViewerStatus.NOT_JOINED
        }

    private fun invitationNotFound(): Nothing = throw NotFoundException(BoardErrorCode.INVITATION_NOT_FOUND)
}
