package com.github.nexters.ppotto.board.application.port

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId

fun interface BoardAnalysisActivityPort {
    fun hasActiveAnalysis(
        boardId: BoardId,
        userId: UserId,
    ): Boolean
}

fun interface BoardActiveAnalysisPort {
    fun findByUserId(userId: UserId): BoardActiveAnalysis?
}

data class BoardActiveAnalysis(
    val boardId: BoardId,
    val status: BoardAnalysisStatus,
)

enum class BoardAnalysisStatus {
    UPLOADING,
    ANALYZING,
}
