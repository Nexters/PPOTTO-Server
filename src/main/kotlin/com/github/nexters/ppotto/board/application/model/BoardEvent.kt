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
        fun layoutUpdated(boardId: BoardId): BoardEvent =
            BoardEvent(
                eventId = UUID.randomUUID(),
                type = BoardEventType.BOARD_LAYOUT_UPDATED,
                boardId = boardId,
                occurredAt = Instant.now(),
            )
    }
}

enum class BoardEventType {
    BOARD_LAYOUT_UPDATED,
}
