package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardDetailV2Response
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards", version = "2+")
@Tag(name = "보드", description = "보드 조회와 관리")
interface BoardDetailV2Api {
    @GetMapping("/{boardId}")
    @Operation(
        operationId = "getV2",
        summary = "보드 상세 조회 (v2)",
        description =
            "보드와 배치된 스티커, 선, 텍스트를 함께 반환함. drawings는 type이 판별자인 합집합이고, " +
                "겹침 순서는 zIndex 필드로 내려감. imageUrl은 만료가 있으므로 진입할 때마다 새로 조회함",
        parameters = [
            Parameter(
                name = "boardId",
                description = "조회할 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "보드 상태",
    )
    @BoardNotFoundApiResponse
    fun get(
        userId: UserId,
        boardId: BoardId,
    ): ApiResponse<BoardDetailV2Response>
}
