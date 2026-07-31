package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.sticker.application.StickerCommandService
import com.github.nexters.ppotto.sticker.application.StickerQueryService
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleRequest
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
class StickerController(
    private val stickerQueryService: StickerQueryService,
    private val stickerCommandService: StickerCommandService,
) {
    @GetMapping("/{stickerId}")
    fun getRecap(
        @AuthenticationPrincipal userId: UUID?,
        @PathVariable stickerId: UUID,
    ): ApiResponse<RecapDetailResponse> =
        ApiResponse.success(
            RecapDetailResponse.from(stickerQueryService.getRecap(userId.orThrow(), stickerId)),
        )

    @PatchMapping("/{stickerId}")
    fun updateTitle(
        @AuthenticationPrincipal userId: UUID?,
        @PathVariable stickerId: UUID,
        @Valid @RequestBody request: UpdateStickerTitleRequest,
    ): ApiResponse<UpdateStickerTitleResponse> =
        ApiResponse.success(
            UpdateStickerTitleResponse.from(stickerCommandService.rename(userId.orThrow(), stickerId, request.title)),
        )

    @DeleteMapping("/{stickerId}")
    fun delete(
        @AuthenticationPrincipal userId: UUID?,
        @PathVariable stickerId: UUID,
    ): ApiResponse<Unit> {
        stickerCommandService.delete(userId.orThrow(), stickerId)
        return ApiResponse.success()
    }

    @PostMapping("/{stickerId}/view")
    fun markViewed(
        @AuthenticationPrincipal userId: UUID?,
        @PathVariable stickerId: UUID,
    ): ApiResponse<Unit> {
        stickerCommandService.markViewed(userId.orThrow(), stickerId)
        return ApiResponse.success()
    }

    private fun UUID?.orThrow(): UUID = this ?: throw UnauthorizedException()
}
