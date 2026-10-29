package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationIssueResponse
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/boards/{boardId}/invitations", version = "1+")
@Tag(name = "보드 초대", description = "보드 초대 코드 발급과 조회")
interface BoardInvitationApi {
    @PostMapping
    @Operation(
        operationId = "issueBoardInvitation",
        summary = "내 초대 코드 발급·재사용",
        description = "보드의 방장 또는 참여자에게 30일 동안 유효한 초대 코드를 발급함",
        parameters = [
            Parameter(
                name = "boardId",
                description = "초대할 보드 ID (uuidv7)",
                example = "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "초대 코드와 미리보기 필요 여부",
    )
    @BoardNotFoundApiResponse
    fun issue(
        userId: UserId,
        boardId: BoardId,
    ): ApiResponse<BoardInvitationIssueResponse>
}
