package com.github.nexters.ppotto.board.presentation.dto

import com.github.nexters.ppotto.board.application.model.BoardShareResult
import com.github.nexters.ppotto.board.domain.BoardShareScope
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "내 보드 공유 링크 정보")
data class BoardShareResponse(
    @field:Schema(
        description = "공유 링크 토큰. 다시 공유해도 같은 값이 유지됨",
        example = "4f1c2b9e-7a3d-4e8f-9b6a-2c5d8e1f0a7b",
    )
    val shareToken: String,

    @field:Schema(description = "현재 공유 범위", example = "RECAP", enumAsRef = true)
    val scope: BoardShareScope,
) {
    companion object {
        fun from(result: BoardShareResult): BoardShareResponse =
            BoardShareResponse(
                shareToken = result.shareToken,
                scope = result.scope,
            )
    }
}
