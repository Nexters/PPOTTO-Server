package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId

class BoardLayoutConflictException(
    val stickerIds: Set<StickerId> = emptySet(),
    val drawingIds: Set<DrawingId> = emptySet(),
    cause: Throwable? = null,
) : ConflictException(BoardErrorCode.LAYOUT_CONFLICT, cause = cause)
