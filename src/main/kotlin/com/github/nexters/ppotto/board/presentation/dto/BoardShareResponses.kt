package com.github.nexters.ppotto.board.presentation.dto

import com.github.nexters.ppotto.board.application.model.BoardShareResult
import com.github.nexters.ppotto.board.application.model.SharedBoard
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

@Schema(description = "공유된 보드")
data class SharedBoardResponse(
    @field:Schema(description = "현재 보드 이름", example = "제주 여행")
    val boardName: String,

    @field:Schema(description = "이 링크를 공유한 현재 사용자 이름", example = "김용희")
    val sharerName: String,

    @field:Schema(description = "이 링크의 현재 공유 범위. BOARD이면 스티커를 눌러도 리캡을 열지 않음", example = "RECAP", enumAsRef = true)
    val scope: BoardShareScope,

    @field:Schema(description = "보드의 모든 참여자 스티커. v2 보드 상세와 같은 스키마이며 isNew는 항상 false")
    val stickers: List<BoardStickerResponse>,

    @field:Schema(description = "보드와 스티커 위의 선과 텍스트. v2 보드 상세와 같은 스키마")
    val drawings: List<DrawingV2Response>,
) {
    companion object {
        fun from(board: SharedBoard): SharedBoardResponse =
            SharedBoardResponse(
                boardName = board.boardName,
                sharerName = board.sharerName,
                scope = board.scope,
                stickers = board.stickers.map(BoardStickerResponse::from),
                drawings = board.drawings.map(DrawingV2Response::from),
            )
    }
}
