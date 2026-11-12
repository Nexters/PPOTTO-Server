package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.global.config.AsyncConfig
import com.github.nexters.ppotto.global.identifier.BoardId
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Component
class BoardEventBroker {
    private val connections = ConcurrentHashMap<BoardId, ConcurrentHashMap<UUID, BoardEventConnection>>()

    fun subscribe(
        boardId: BoardId,
        connection: BoardEventConnection,
    ): BoardEventSubscription {
        val subscriptionId = UUID.randomUUID()
        connections
            .computeIfAbsent(boardId) { ConcurrentHashMap() }[subscriptionId] = connection
        return BoardEventSubscription { remove(boardId, subscriptionId) }
    }

    @Async(AsyncConfig.BOARD_EVENT_TASK_EXECUTOR)
    fun publish(event: BoardEvent) {
        connections[event.boardId]
            ?.toMap()
            ?.forEach { (subscriptionId, connection) ->
                if (!connection.send(event)) {
                    remove(event.boardId, subscriptionId)
                    connection.complete()
                }
            }
    }

    fun heartbeat() {
        connections
            .toMap()
            .forEach { (boardId, boardConnections) ->
                boardConnections
                    .toMap()
                    .forEach { (subscriptionId, connection) ->
                        if (!connection.heartbeat()) {
                            remove(boardId, subscriptionId)
                            connection.complete()
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
