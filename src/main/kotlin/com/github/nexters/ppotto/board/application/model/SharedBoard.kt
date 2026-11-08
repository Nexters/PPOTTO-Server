package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.board.domain.Drawing

data class SharedBoard(
    val boardName: String,
    val sharerName: String,
    val scope: BoardShareScope,
    val stickers: List<BoardStickerItem>,
    val drawings: List<Drawing>,
)
