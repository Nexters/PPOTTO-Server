package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardDetailResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards", version = "1")
@Tag(name = "보드", description = "보드 조회와 관리")
interface BoardDetailApi {
    @GetMapping("/{boardId}")
    @Operation(
        operationId = "get",
        summary = "보드 상세 조회",
        description =
            "보드와 배치된 스티커, 선을 함께 반환함. imageUrl은 만료가 있으므로 진입할 때마다 새로 조회함. " +
                "겹침 순서는 stroke JSON의 zIndex 키에 담겨 내려가며, 텍스트는 v2에서만 내려감",
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
    ): ApiResponse<BoardDetailResponse>
}
