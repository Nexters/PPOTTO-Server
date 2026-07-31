package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.openapi.NotFoundApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.sticker.application.StickerCommandService
import com.github.nexters.ppotto.sticker.application.StickerQueryService
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleRequest
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleResponse
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
@RequestMapping("/stickers", version = "1")
@Tag(name = "스티커", description = "스티커와 리캡 관리")
class StickerController(
    private val stickerQueryService: StickerQueryService,
    private val stickerCommandService: StickerCommandService,
) {
    @GetMapping("/{stickerId}")
    @Operation(
        summary = "리캡 상세 조회",
        description = "스티커 정보와 분석 코멘트, 관련 사진을 반환함",
    )
    @NotFoundApiResponse
    fun getRecap(
        @AuthenticatedUser userId: UUID,
        @PathVariable stickerId: UUID,
    ): ApiResponse<RecapDetailResponse> =
        ApiResponse.success(
            RecapDetailResponse.from(stickerQueryService.getRecap(userId, stickerId)),
        )

    @PatchMapping("/{stickerId}")
    @Operation(
        summary = "스티커 제목 수정",
        description = "내 스티커의 제목을 변경함",
    )
    @InvalidInputApiResponse
    @NotFoundApiResponse
    fun updateTitle(
        @AuthenticatedUser userId: UUID,
        @PathVariable stickerId: UUID,
        @Valid @RequestBody request: UpdateStickerTitleRequest,
    ): ApiResponse<UpdateStickerTitleResponse> =
        ApiResponse.success(
            UpdateStickerTitleResponse.from(stickerCommandService.rename(userId, stickerId, request.title)),
        )

    @DeleteMapping("/{stickerId}")
    @Operation(
        summary = "스티커 묶음 삭제",
        description = "스티커와 리캡 하위 데이터를 함께 삭제함",
    )
    @NotFoundApiResponse
    fun delete(
        @AuthenticatedUser userId: UUID,
        @PathVariable stickerId: UUID,
    ): ApiResponse<Unit> {
        stickerCommandService.delete(userId, stickerId)
        return ApiResponse.success()
    }

    @PostMapping("/{stickerId}/view")
    @Operation(
        summary = "리캡 열람 처리",
        description = "새 리캡 표시를 제거하며 여러 번 호출해도 같은 결과를 보장함",
    )
    @NotFoundApiResponse
    fun markViewed(
        @AuthenticatedUser userId: UUID,
        @PathVariable stickerId: UUID,
    ): ApiResponse<Unit> {
        stickerCommandService.markViewed(userId, stickerId)
        return ApiResponse.success()
    }
}
