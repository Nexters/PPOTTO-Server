package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.presentation.dto.BoardInvitationPreviewUploadResponse
import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.multipart.MultipartFile
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/board-invitations/{invitationId}/preview", version = "1+")
@Tag(name = "보드 초대", description = "보드 초대 코드 발급과 조회")
interface BoardInvitationPreviewApi {
    @PutMapping(consumes = ["multipart/form-data"])
    @Operation(
        operationId = "uploadBoardInvitationPreview",
        summary = "초대 미리보기 업로드·교체",
        description = "발급자가 현재 코드에 사용할 WebP 미리보기를 업로드함",
        parameters = [
            Parameter(
                name = "invitationId",
                description = "초대 ID (uuidv7)",
                example = "019a268d-15c8-7d31-8f42-90ad72d627f5",
            ),
        ],
    )
    @OpenApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "미리보기 저장 및 교체 완료")
    @OpenApiResponse(
        responseCode = "400",
        description = "초대 미리보기 이미지가 올바르지 않음 (BOARD-007)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "404",
        description = "초대를 찾을 수 없음 (BOARD-006)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    @OpenApiResponse(
        responseCode = "409",
        description = "이미지 생성 중 초대 코드가 갱신됨 (BOARD-008)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiErrorResponse::class))],
    )
    fun upload(
        userId: UserId,
        invitationId: BoardInvitationId,
        code: String,
        file: MultipartFile,
    ): ApiResponse<BoardInvitationPreviewUploadResponse>
}
