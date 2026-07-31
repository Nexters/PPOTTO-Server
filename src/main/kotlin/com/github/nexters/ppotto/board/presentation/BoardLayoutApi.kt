package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardLayoutRequest
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.openapi.NotFoundApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID

@RequestMapping("/boards", version = "1")
@Tag(name = "보드", description = "보드 조회와 관리")
interface BoardLayoutApi {
    @PatchMapping("/{boardId}/layout")
    @Operation(
        summary = "보드 편집 결과 저장",
        description = "스티커 배치와 그림 생성·삭제를 하나의 트랜잭션으로 반영함",
    )
    @InvalidInputApiResponse
    @NotFoundApiResponse
    fun update(
        userId: UUID,
        boardId: UUID,
        request: BoardLayoutRequest,
    ): ApiResponse<Unit>
}
