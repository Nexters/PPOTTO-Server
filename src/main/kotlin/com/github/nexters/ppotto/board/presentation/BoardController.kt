package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardCommandService
import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.board.presentation.dto.BoardDetailResponse
import com.github.nexters.ppotto.board.presentation.dto.BoardResponse
import com.github.nexters.ppotto.board.presentation.dto.CreateBoardRequest
import com.github.nexters.ppotto.board.presentation.dto.RenameBoardRequest
import com.github.nexters.ppotto.global.openapi.ConflictApiResponse
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.openapi.NotFoundApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/boards", version = "1")
@Tag(name = "보드", description = "보드 조회와 관리")
class BoardController(
    private val boardCommandService: BoardCommandService,
    private val boardQueryService: BoardQueryService,
) {
    @GetMapping
    @Operation(
        summary = "내 보드 목록 조회",
        description = "보드 선택 드롭다운에 사용할 활성 보드 목록을 반환함",
    )
    fun list(
        @AuthenticatedUser userId: UUID,
    ): ApiResponse<List<BoardResponse>> =
        ApiResponse.success(
            boardQueryService.list(userId).map(BoardResponse::from),
        )

    @PostMapping
    @Operation(
        summary = "보드 생성",
        description = "새 보드를 만들며 이름을 생략하면 순서에 맞는 기본 이름을 사용함",
    )
    @InvalidInputApiResponse
    @ConflictApiResponse
    fun create(
        @AuthenticatedUser userId: UUID,
        @Valid @RequestBody request: CreateBoardRequest,
    ): ApiResponse<BoardResponse> =
        ApiResponse.success(
            BoardResponse.from(boardCommandService.create(userId, request.name)),
        )

    @GetMapping("/{boardId}")
    @Operation(
        summary = "보드 상세 조회",
        description = "보드와 배치된 스티커, 그림을 함께 반환함",
    )
    @NotFoundApiResponse
    fun get(
        @AuthenticatedUser userId: UUID,
        @PathVariable boardId: UUID,
    ): ApiResponse<BoardDetailResponse> =
        ApiResponse.success(
            BoardDetailResponse.from(boardQueryService.getDetail(boardId, userId)),
        )

    @PatchMapping("/{boardId}")
    @Operation(
        summary = "보드 이름 변경",
        description = "내 보드의 이름을 변경함",
    )
    @InvalidInputApiResponse
    @NotFoundApiResponse
    fun rename(
        @AuthenticatedUser userId: UUID,
        @PathVariable boardId: UUID,
        @Valid @RequestBody request: RenameBoardRequest,
    ): ApiResponse<BoardResponse> =
        ApiResponse.success(
            BoardResponse.from(boardCommandService.rename(boardId, userId, request.name)),
        )

    @DeleteMapping("/{boardId}")
    @Operation(
        summary = "보드 삭제",
        description = "내 보드를 삭제하며 마지막 보드나 분석 중인 보드는 삭제할 수 없음",
    )
    @NotFoundApiResponse
    @ConflictApiResponse
    fun delete(
        @AuthenticatedUser userId: UUID,
        @PathVariable boardId: UUID,
    ): ApiResponse<Unit> {
        boardCommandService.delete(boardId, userId)
        return ApiResponse.success()
    }
}
