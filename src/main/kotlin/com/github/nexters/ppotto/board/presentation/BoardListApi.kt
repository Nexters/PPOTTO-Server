package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardResponse
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards", version = "1")
@Tag(name = "보드", description = "보드 조회와 관리")
interface BoardListApi {
    @GetMapping
    @Operation(
        operationId = "list",
        summary = "내 보드 목록 조회",
        description =
            "보드 선택 드롭다운에 사용할 내가 만든 활성 보드 목록을 반환함. 참여한 보드는 포함하지 않음. " +
                "id(uuidv7) 오름차순이 생성순",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "보드 목록",
    )
    fun list(userId: UserId): ApiResponse<List<BoardResponse>>
}
