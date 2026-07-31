package com.github.nexters.ppotto.board.presentation.dto

import com.github.nexters.ppotto.board.domain.Board
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateBoardRequest(
    @field:Size(min = 1, max = Board.MAX_NAME_LENGTH)
    val name: String? = null,
)

data class RenameBoardRequest(
    @field:NotBlank
    @field:Size(max = Board.MAX_NAME_LENGTH)
    val name: String,
)
