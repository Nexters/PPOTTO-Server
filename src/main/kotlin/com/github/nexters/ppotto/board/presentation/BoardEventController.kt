package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardEventSubscriptionService
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration

@RestController
class BoardEventController(
    private val boardEventSubscriptionService: BoardEventSubscriptionService,
) : BoardEventApi {
    override fun subscribe(
        @AuthenticatedUser userId: UserId,
        @PathVariable boardId: BoardId,
    ): SseEmitter {
        val emitter = SseEmitter(CONNECTION_TIMEOUT.toMillis())
        val connection = SseBoardEventConnection(emitter)
        val subscription = boardEventSubscriptionService.subscribe(boardId, userId, connection)

        emitter.onCompletion(subscription::close)
        emitter.onError { subscription.close() }
        emitter.onTimeout {
            subscription.close()
            connection.complete()
        }

        if (!connection.heartbeat()) {
            subscription.close()
            connection.complete()
        }
        return emitter
    }

    companion object {
        private val CONNECTION_TIMEOUT: Duration = Duration.ofMinutes(30)
    }
}
