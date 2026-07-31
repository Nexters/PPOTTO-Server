package com.github.nexters.ppotto.board.presentation.dto

import com.github.nexters.ppotto.board.domain.Board
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "보드 생성 요청")
data class CreateBoardRequest(
    @field:Size(min = 1, max = Board.MAX_NAME_LENGTH)
    @field:Schema(description = "보드 이름. 생략하면 기본 이름을 생성함", example = "여름 휴가")
    val name: String? = null,
)

@Schema(description = "보드 이름 변경 요청")
data class RenameBoardRequest(
    @field:NotBlank
    @field:Size(max = Board.MAX_NAME_LENGTH)
    @field:Schema(description = "새 보드 이름", example = "여름 휴가")
    val name: String,
)
