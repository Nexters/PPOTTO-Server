package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service

@Service
class BoardEventSubscriptionService(
    private val boardAccessService: BoardAccessService,
    private val boardEventBroker: BoardEventBroker,
) {
    fun subscribe(
        boardId: BoardId,
        userId: UserId,
        connection: BoardEventConnection,
    ): BoardEventSubscription {
        boardAccessService.getAccessibleById(boardId, userId)
        return boardEventBroker.subscribe(boardId, connection)
    }
}
