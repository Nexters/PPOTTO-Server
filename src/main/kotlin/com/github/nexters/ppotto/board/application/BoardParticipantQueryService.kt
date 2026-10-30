package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardParticipant
import com.github.nexters.ppotto.board.application.model.BoardParticipants
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.infrastructure.BoardMembershipRepository
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.UserService
import org.springframework.stereotype.Service

@Service
class BoardParticipantQueryService(
    private val boardAccessService: BoardAccessService,
    private val boardMembershipRepository: BoardMembershipRepository,
    private val userService: UserService,
) {
    fun get(
        boardId: BoardId,
        userId: UserId,
    ): BoardParticipants = get(boardAccessService.getReadableById(boardId, userId))

    internal fun get(board: Board): BoardParticipants {
        val memberUserIds = boardMembershipRepository.findMemberUserIds(board.id)
        val participantUserIds = listOf(board.userId) + memberUserIds
        val names = userService.findDisplayNames(participantUserIds)

        return BoardParticipants(
            participantCount = participantUserIds.size,
            maxParticipantCount = Board.MAX_PARTICIPANT_COUNT,
            participants =
                participantUserIds.map { participantUserId ->
                    BoardParticipant(
                        userId = participantUserId,
                        name =
                            checkNotNull(names[participantUserId]) {
                                "보드 참여자 이름이 누락되었습니다: $participantUserId"
                            },
                        role = if (participantUserId == board.userId) BoardRole.OWNER else BoardRole.MEMBER,
                    )
                },
        )
    }
}
