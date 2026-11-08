package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.domain.BoardShareScope

data class BoardShareResult(
    val shareToken: String,
    val scope: BoardShareScope,
)
