package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardInvitationDetail
import com.github.nexters.ppotto.board.application.model.BoardInvitationParticipant
import com.github.nexters.ppotto.board.application.model.BoardInvitationViewerStatus
import com.github.nexters.ppotto.board.application.port.BoardInvitationPreviewStorage
import com.github.nexters.ppotto.board.application.port.BoardStickerSummaryPort
import com.github.nexters.ppotto.board.domain.Board
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
            boardInvitationRepository.findByCode(normalizedCode)
                ?.takeIf { it.expiresAt.isAfter(Instant.now()) }
                ?: invitationNotFound()
        val board = boardRepository.findById(invitation.boardId) ?: invitationNotFound()
        val inviterName = userService.findActiveName(invitation.inviterUserId) ?: invitationNotFound()
        val memberUserIds = boardRepository.findMemberUserIds(board.id)
        val participantUserIds = listOf(board.userId) + memberUserIds
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
            participantCount = participantUserIds.size,
            maxParticipantCount = Board.MAX_PARTICIPANT_COUNT,
            stickerCount = stickerCount,
            previewImageUrl = invitation.previewImageKey?.let(previewStorage::issueReadUrl),
            expiresAt = invitation.expiresAt,
            viewerStatus = currentUserId?.let { viewerStatus(it, board.userId, memberUserIds) },
            participants = currentUserId?.let { participants(participantUserIds, board.userId) },
        )
    }

    private fun viewerStatus(
        currentUserId: UserId,
        ownerId: UserId,
        memberUserIds: List<UserId>,
    ): BoardInvitationViewerStatus =
        when {
            currentUserId == ownerId -> BoardInvitationViewerStatus.OWNER
            currentUserId in memberUserIds -> BoardInvitationViewerStatus.MEMBER
            else -> BoardInvitationViewerStatus.NOT_JOINED
        }

    private fun participants(
        participantUserIds: List<UserId>,
        ownerId: UserId,
    ): List<BoardInvitationParticipant> {
        val names = userService.findDisplayNames(participantUserIds)
        return participantUserIds.map { userId ->
            BoardInvitationParticipant(
                name = checkNotNull(names[userId]) { "보드 참여자 이름이 누락되었습니다: $userId" },
                role = if (userId == ownerId) BoardRole.OWNER else BoardRole.MEMBER,
            )
        }
    }

    private fun invitationNotFound(): Nothing = throw NotFoundException(BoardErrorCode.INVITATION_NOT_FOUND)
}
