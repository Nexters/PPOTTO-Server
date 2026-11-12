package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.board.presentation.dto.BoardEventResponse
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

class SseBoardEventConnection(
    private val emitter: SseEmitter,
) : BoardEventConnection {
    override fun send(event: BoardEvent): Boolean =
        sendSafely(
            SseEmitter
                .event()
                .id(event.eventId.toString())
                .name(event.type.name)
                .reconnectTime(RECONNECT_TIME_MILLIS)
                .data(BoardEventResponse.from(event)),
        )

    override fun heartbeat(): Boolean = sendSafely(SseEmitter.event().comment(HEARTBEAT_COMMENT))

    override fun complete() {
        emitter.complete()
    }

    private fun sendSafely(event: SseEmitter.SseEventBuilder): Boolean =
        try {
            emitter.send(event)
            true
        } catch (_: IOException) {
            false
        } catch (_: IllegalStateException) {
            false
        }

    companion object {
        const val RECONNECT_TIME_MILLIS = 3_000L
        private const val HEARTBEAT_COMMENT = "heartbeat"
    }
}
