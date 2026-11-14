package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.global.identifier.BoardId
import java.time.Instant
import java.util.UUID

data class BoardEvent(
    val eventId: UUID,
    val type: BoardEventType,
    val boardId: BoardId,
    val occurredAt: Instant,
) {
    companion object {
        fun updated(boardId: BoardId): BoardEvent =
            BoardEvent(
                eventId = UUID.randomUUID(),
                type = BoardEventType.BOARD_UPDATED,
                boardId = boardId,
                occurredAt = Instant.now(),
            )

        fun participantsUpdated(boardId: BoardId): BoardEvent =
            BoardEvent(
                eventId = UUID.randomUUID(),
                type = BoardEventType.BOARD_PARTICIPANTS_UPDATED,
                boardId = boardId,
                occurredAt = Instant.now(),
            )

        fun deleted(boardId: BoardId): BoardEvent =
            BoardEvent(
                eventId = UUID.randomUUID(),
                type = BoardEventType.BOARD_DELETED,
                boardId = boardId,
                occurredAt = Instant.now(),
            )
    }
}

enum class BoardEventType {
    BOARD_UPDATED,
    BOARD_PARTICIPANTS_UPDATED,
    BOARD_DELETED,
}
