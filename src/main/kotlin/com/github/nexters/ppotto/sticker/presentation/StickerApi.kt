package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.web.ApiResponse
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import com.github.nexters.ppotto.sticker.presentation.dto.ShareRecapRequest
import com.github.nexters.ppotto.sticker.presentation.dto.ShareRecapResponse
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateRecapCommentPositionsRequest
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

@RequestMapping("/stickers", version = "1+")
@Tag(name = "스티커", description = "스티커와 리캡 관리")
interface StickerApi {
    @GetMapping("/{stickerId}")
    @Operation(
        operationId = "getRecap",
        summary = "리캡 상세 조회",
        description =
            "내 스티커 정보와 분석 코멘트, 관련 사진을 반환함. 본인 스티커만 조회할 수 있고 빨간 점 제거는 /view를 따로 호출함. " +
                "현재 공유 중이면 share 객체가 함께 내려옴",
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

    @GetMapping("/shared/{shareToken}")
    @Operation(
        operationId = "getSharedRecap",
        summary = "공유된 리캡 조회",
        description =
            "공유 토큰으로 리캡을 조회함. 인증이 필요 없는 유일한 리캡 경로이며, 공유가 해제되었거나 없는 토큰은 STICKER-001로 응답함. " +
                "공유할 때 사진을 포함하지 않았다면 photos는 항상 빈 배열이고 isNew는 항상 false임",
        parameters = [
            Parameter(
                name = "shareToken",
                description = "공유 링크 토큰",
                example = "01983f30-0000-7000-8000-000000000000",
            ),
        ],
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "공유된 리캡 상세",
    )
    @StickerNotFoundApiResponse
    fun getSharedRecap(shareToken: String): ApiResponse<RecapDetailResponse>

    @PostMapping("/{stickerId}/share")
    @Operation(
        operationId = "share",
        summary = "리캡 공유 시작",
        description =
            "내 리캡의 공유 링크 토큰을 발급함. 이미 공유 중이면 같은 토큰을 유지한 채 사진 포함 여부만 갱신하므로 이미 보낸 링크가 끊기지 않음",
        parameters = [
            Parameter(
                name = "stickerId",
                description = "공유할 스티커 ID (uuidv7)",
                example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ShareRecapRequest::class),
                    ),
                ],
            ),
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "발급된 공유 정보",
    )
    @StickerNotFoundApiResponse
    fun share(
        userId: UserId,
        stickerId: StickerId,
        request: ShareRecapRequest,
    ): ApiResponse<ShareRecapResponse>

    @DeleteMapping("/{stickerId}/share")
    @Operation(
        operationId = "unshare",
        summary = "리캡 공유 해제",
        description = "공유 토큰을 즉시 무효화함. 이미 공유 중이 아니어도 같은 결과를 보장함",
        parameters = [
            Parameter(
                name = "stickerId",
                description = "공유를 해제할 스티커 ID (uuidv7)",
                example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
    )
    @EmptySuccessApiResponse
    @StickerNotFoundApiResponse
    fun unshare(
        userId: UserId,
        stickerId: StickerId,
    ): ApiResponse<Unit>

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

    @PatchMapping("/{stickerId}/comments")
    @Operation(
        operationId = "updateCommentPositions",
        summary = "리캡 코멘트 위치 일괄 수정",
        description = "이미 위치가 있는 말풍선 코멘트만 대상으로, 리캡 상세 화면에서 바뀐 위치만 일괄 저장함. 하단 키워드 칩(posX/posY가 없던 코멘트)의 id는 허용되지 않음",
        parameters = [
            Parameter(
                name = "stickerId",
                description = "코멘트가 속한 스티커 ID (uuidv7)",
                example = "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            ),
        ],
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = UpdateRecapCommentPositionsRequest::class),
                    ),
                ],
            ),
    )
    @EmptySuccessApiResponse
    @UneditableRecapCommentApiResponse
    @StickerNotFoundApiResponse
    fun updateCommentPositions(
        userId: UserId,
        stickerId: StickerId,
        request: UpdateRecapCommentPositionsRequest,
    ): ApiResponse<Unit>

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
    @NotRegeneratableStickerApiResponse
    @StickerNotFoundApiResponse
    @StickerRegenerationInProgressApiResponse
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
