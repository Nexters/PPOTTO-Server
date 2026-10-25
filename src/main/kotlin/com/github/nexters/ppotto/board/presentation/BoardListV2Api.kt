package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardListV2Response
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards", version = "2+")
@Tag(name = "보드", description = "보드 조회와 관리")
interface BoardListV2Api {
    @GetMapping
    @Operation(
        operationId = "listV2",
        summary = "홈 보드 목록 조회 (v2)",
        description =
            "홈 화면 카드용으로 내가 만든 보드와 참여한 보드를 함께 반환함. 삭제된 보드와 참여하지 않은 다른 사용자의 보드는 내려가지 않음. " +
                "lastActivityAt 내림차순, 같으면 id 내림차순으로 정렬하며 페이지네이션 없이 전부 반환함. " +
                "counts는 탭별 개수이고 previewImageUrls는 만료가 있으므로 진입할 때마다 새로 조회함",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "보드 목록과 탭별 개수",
    )
    fun list(userId: UserId): ApiResponse<BoardListV2Response>
}
