package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.global.config.AsyncConfig
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Component
class BoardEventBroker {
    private val connections = ConcurrentHashMap<BoardId, ConcurrentHashMap<UUID, BoardEventSubscriber>>()

    fun subscribe(
        boardId: BoardId,
        userId: UserId,
        connection: BoardEventConnection,
    ): BoardEventSubscription {
        val subscriptionId = UUID.randomUUID()
        connections.compute(boardId) { _, boardConnections ->
            (boardConnections ?: ConcurrentHashMap()).also {
                it[subscriptionId] = BoardEventSubscriber(userId, connection)
            }
        }
        return BoardEventSubscription { remove(boardId, subscriptionId) }
    }

    fun disconnect(
        boardId: BoardId,
        userId: UserId,
    ) {
        connections[boardId]
            ?.toMap()
            ?.forEach { (subscriptionId, subscriber) ->
                if (subscriber.userId == userId) {
                    remove(boardId, subscriptionId)
                    subscriber.connection.complete()
                }
            }
    }

    @Async(AsyncConfig.BOARD_EVENT_TASK_EXECUTOR)
    fun publish(event: BoardEvent) {
        connections[event.boardId]
            ?.toMap()
            ?.forEach { (subscriptionId, subscriber) ->
                if (!subscriber.connection.send(event)) {
                    remove(event.boardId, subscriptionId)
                    subscriber.connection.complete()
                }
            }
    }

    fun heartbeat() {
        connections
            .toMap()
            .forEach { (boardId, boardConnections) ->
                boardConnections
                    .toMap()
                    .forEach { (subscriptionId, subscriber) ->
                        if (!subscriber.connection.heartbeat()) {
                            remove(boardId, subscriptionId)
                            subscriber.connection.complete()
                        }
                    }
            }
    }

    private fun remove(
        boardId: BoardId,
        subscriptionId: UUID,
    ) {
        connections.computeIfPresent(boardId) { _, boardConnections ->
            boardConnections.remove(subscriptionId)
            boardConnections.takeUnless { it.isEmpty() }
        }
    }
}

private data class BoardEventSubscriber(
    val userId: UserId,
    val connection: BoardEventConnection,
)

class BoardEventSubscription internal constructor(
    private val unsubscribe: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            unsubscribe()
        }
    }
}
