package com.github.nexters.ppotto.board.presentation.dto

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.model.BoardEventType
import com.github.nexters.ppotto.global.identifier.BoardId
import java.time.Instant
import java.util.UUID

data class BoardEventResponse(
    val eventId: UUID,
    val type: BoardEventType,
    val boardId: BoardId,
    val occurredAt: Instant,
) {
    companion object {
        fun from(event: BoardEvent): BoardEventResponse =
            BoardEventResponse(
                eventId = event.eventId,
                type = event.type,
                boardId = event.boardId,
                occurredAt = event.occurredAt,
            )
    }
}
