package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardJoinResult
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardInvitation
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.infrastructure.BoardInvitationRepository
import com.github.nexters.ppotto.board.infrastructure.BoardMembershipRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale

@Service
class BoardParticipationService(
    private val boardInvitationRepository: BoardInvitationRepository,
    private val boardMembershipRepository: BoardMembershipRepository,
    private val boardRepository: BoardRepository,
    private val userService: UserService,
) {
    @Transactional
    fun join(
        code: String,
        userId: UserId,
    ): BoardJoinResult {
        if (!userService.isActive(userId)) throw UnauthorizedException()

        val normalizedCode = code.uppercase(Locale.ROOT)
        val candidate = boardInvitationRepository.findByCode(normalizedCode) ?: invitationNotFound()
        val board = boardRepository.findByIdForUpdate(candidate.boardId) ?: invitationNotFound()
        val invitation = boardInvitationRepository.findById(candidate.id)
        validateInvitation(invitation, board, normalizedCode)

        val role = resolveRoleOrAddMember(board, userId)
        return board.toJoinResult(role)
    }

    private fun resolveRoleOrAddMember(
        board: Board,
        userId: UserId,
    ): BoardRole =
        when {
            board.userId == userId -> BoardRole.OWNER
            boardMembershipRepository.exists(board.id, userId) -> BoardRole.MEMBER
            else -> {
                if (boardMembershipRepository.countMembersByBoardId(board.id) >= Board.MAX_PARTICIPANT_COUNT - 1) {
                    throw ConflictException(BoardErrorCode.PARTICIPANT_LIMIT_EXCEEDED)
                }
                boardMembershipRepository.saveIfAbsent(board.id, userId)
                BoardRole.MEMBER
            }
        }

    private fun validateInvitation(
        invitation: BoardInvitation?,
        board: Board,
        normalizedCode: String,
    ) {
        if (invitation == null || !invitation.isUsableFor(board, normalizedCode)) invitationNotFound()
        if (!userService.isActive(invitation.inviterUserId)) invitationNotFound()
    }

    private fun BoardInvitation.isUsableFor(
        board: Board,
        normalizedCode: String,
    ): Boolean = boardId == board.id && code == normalizedCode && expiresAt.isAfter(Instant.now())

    private fun Board.toJoinResult(role: BoardRole): BoardJoinResult =
        BoardJoinResult(
            boardId = id,
            boardName = name,
            role = role,
        )

    private fun invitationNotFound(): Nothing = throw NotFoundException(BoardErrorCode.INVITATION_NOT_FOUND)
}
