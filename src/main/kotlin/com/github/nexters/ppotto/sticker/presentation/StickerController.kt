package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.sticker.application.StickerCommandService
import com.github.nexters.ppotto.sticker.application.StickerQueryService
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleRequest
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class StickerController(
    private val stickerQueryService: StickerQueryService,
    private val stickerCommandService: StickerCommandService,
) : StickerApi {
    override fun getRecap(
        @AuthenticatedUser userId: UUID,
        @PathVariable stickerId: UUID,
    ): ApiResponse<RecapDetailResponse> =
        stickerQueryService
            .getRecap(userId, stickerId)
            .let(RecapDetailResponse::from)
            .let { ApiResponse.success(it) }

    override fun updateTitle(
        @AuthenticatedUser userId: UUID,
        @PathVariable stickerId: UUID,
        @Valid @RequestBody request: UpdateStickerTitleRequest,
    ): ApiResponse<UpdateStickerTitleResponse> =
        stickerCommandService
            .rename(userId, stickerId, request.title)
            .let(UpdateStickerTitleResponse::from)
            .let { ApiResponse.success(it) }

    override fun delete(
        @AuthenticatedUser userId: UUID,
        @PathVariable stickerId: UUID,
    ): ApiResponse<Unit> =
        stickerCommandService
            .delete(userId, stickerId)
            .let { ApiResponse.success() }

    override fun markViewed(
        @AuthenticatedUser userId: UUID,
        @PathVariable stickerId: UUID,
    ): ApiResponse<Unit> =
        stickerCommandService
            .markViewed(userId, stickerId)
            .let { ApiResponse.success() }
}
