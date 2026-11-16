package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardEventConnection
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BoardEventSubscriptionService(
    private val boardAccessService: BoardAccessService,
    private val boardEventBroker: BoardEventBroker,
) {
    @Transactional
    fun subscribe(
        boardId: BoardId,
        userId: UserId,
        connection: BoardEventConnection,
    ): BoardEventSubscription {
        boardAccessService.getAccessibleByIdForUpdate(boardId, userId)
        return boardEventBroker.subscribe(boardId, userId, connection)
    }
}
