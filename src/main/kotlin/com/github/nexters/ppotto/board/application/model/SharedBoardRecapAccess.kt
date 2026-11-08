package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.global.identifier.BoardId

data class SharedBoardRecapAccess(
    val boardId: BoardId,
    val includesPhotos: Boolean,
)
