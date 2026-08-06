package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleRequest
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/stickers", version = "1")
@Tag(name = "스티커", description = "스티커와 리캡 관리")
interface StickerApi {
    @GetMapping("/{stickerId}")
    @Operation(
        operationId = "getRecap",
        summary = "리캡 상세 조회",
        description = "스티커 정보와 분석 코멘트, 관련 사진을 반환함. 빨간 점 제거는 /view를 따로 호출함",
        parameters = [
            Parameter(
                name = "stickerId",
                description = "조회할 스티커 ID (uuidv7)",
                example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "리캡 상세",
    )
    @StickerNotFoundApiResponse
    fun getRecap(
        userId: UserId,
        stickerId: StickerId,
    ): ApiResponse<RecapDetailResponse>

    @PatchMapping("/{stickerId}")
    @Operation(
        operationId = "updateTitle",
        summary = "스티커 제목 수정",
        description = "내 스티커의 제목을 변경함. 최대 15자이며 빨간 점 상태는 바뀌지 않음",
        parameters = [
            Parameter(
                name = "stickerId",
                description = "제목을 바꿀 스티커 ID (uuidv7)",
                example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = UpdateStickerTitleRequest::class),
                    ),
                ],
            ),
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "수정 완료",
    )
    @InvalidInputApiResponse
    @StickerNotFoundApiResponse
    fun updateTitle(
        userId: UserId,
        stickerId: StickerId,
        request: UpdateStickerTitleRequest,
    ): ApiResponse<UpdateStickerTitleResponse>

    @DeleteMapping("/{stickerId}")
    @Operation(
        operationId = "delete",
        summary = "스티커 묶음 삭제",
        description = "스티커와 연결된 그림, 리캡(코멘트·사진 연결)을 함께 삭제함",
        parameters = [
            Parameter(
                name = "stickerId",
                description = "삭제할 스티커 ID (uuidv7)",
                example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
    )
    @EmptySuccessApiResponse
    @StickerNotFoundApiResponse
    fun delete(
        userId: UserId,
        stickerId: StickerId,
    ): ApiResponse<Unit>

    @PostMapping("/{stickerId}/regenerate")
    @Operation(
        operationId = "regenerate",
        summary = "스티커 이미지 재생성",
        description = "고정된 사진 구성은 유지한 채 스티커 이미지(피사체)만 다시 생성함. 리캡 문구(title, summary)는 바뀌지 않음",
        parameters = [
            Parameter(
                name = "stickerId",
                description = "재생성할 스티커 ID (uuidv7)",
                example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "재생성 완료",
    )
    @InvalidInputApiResponse
    @StickerNotFoundApiResponse
    @OpenApiResponse(
        responseCode = "409",
        description = "같은 스티커에 대한 재생성이 이미 진행 중임 (STICKER-002)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
            ),
        ],
    )
    fun regenerate(
        userId: UserId,
        stickerId: StickerId,
    ): ApiResponse<RecapDetailResponse>

    @PostMapping("/{stickerId}/view")
    @Operation(
        operationId = "markViewed",
        summary = "리캡 열람 처리",
        description = "새 리캡 표시(빨간 점)를 제거하며 여러 번 호출해도 같은 결과를 보장함",
        parameters = [
            Parameter(
                name = "stickerId",
                description = "열람 처리할 스티커 ID (uuidv7)",
                example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
    )
    @EmptySuccessApiResponse
    @StickerNotFoundApiResponse
    fun markViewed(
        userId: UserId,
        stickerId: StickerId,
    ): ApiResponse<Unit>
}
