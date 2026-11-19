package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.DrawingId

data class DrawingLayoutUpdateCommand(
    val drawing: NewDrawing,
    val expectedLayoutVersion: Long,
) {
    init {
        if (expectedLayoutVersion < 0) throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
    }
}

data class DrawingLayoutDeleteCommand(
    val id: DrawingId,
    val expectedLayoutVersion: Long,
) {
    init {
        if (expectedLayoutVersion < 0) throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
    }
}
