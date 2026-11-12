package com.github.nexters.ppotto.board.application.port

import com.github.nexters.ppotto.board.application.model.BoardEvent

interface BoardEventConnection {
    fun send(event: BoardEvent): Boolean

    fun heartbeat(): Boolean

    fun complete()
}
