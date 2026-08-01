package com.github.nexters.ppotto.board.application.port

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId

fun interface BoardAnalysisActivityPort {
    fun hasActiveAnalysis(
        boardId: BoardId,
        userId: UserId,
    ): Boolean
}
