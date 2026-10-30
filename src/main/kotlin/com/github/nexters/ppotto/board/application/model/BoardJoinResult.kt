package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.global.identifier.BoardId

data class BoardJoinResult(
    val boardId: BoardId,
    val boardName: String,
    val role: BoardRole,
)
