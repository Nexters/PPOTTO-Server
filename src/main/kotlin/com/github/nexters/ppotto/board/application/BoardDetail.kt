package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.global.identifier.BoardId

data class BoardDetail(
    val id: BoardId,
    val name: String,
    val stickers: List<BoardStickerItem>,
    val drawings: List<Drawing>,
)
