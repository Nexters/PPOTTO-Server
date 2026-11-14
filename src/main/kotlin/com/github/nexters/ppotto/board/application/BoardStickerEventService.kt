package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.model.BoardEvent
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.transaction.afterCommit
import org.springframework.stereotype.Service

@Service
class BoardStickerEventService(
    private val boardEventBroker: BoardEventBroker,
) {
    fun publishUpdated(boardId: BoardId) {
        afterCommit { boardEventBroker.publish(BoardEvent.stickersUpdated(boardId)) }
    }
}
