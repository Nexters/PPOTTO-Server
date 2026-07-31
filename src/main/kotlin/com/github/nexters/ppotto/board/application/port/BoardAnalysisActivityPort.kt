package com.github.nexters.ppotto.board.application.port

import java.util.UUID

fun interface BoardAnalysisActivityPort {
    fun hasActiveAnalysis(
        boardId: UUID,
        userId: UUID,
    ): Boolean
}
