package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.sticker.application.RecapCommentCommandService
import com.github.nexters.ppotto.sticker.application.StickerCommandService
import com.github.nexters.ppotto.sticker.application.StickerQueryService
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateRecapCommentPositionsRequest
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleRequest
import com.github.nexters.ppotto.sticker.presentation.dto.UpdateStickerTitleResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class StickerController(
    private val stickerQueryService: StickerQueryService,
    private val stickerCommandService: StickerCommandService,
    private val recapCommentCommandService: RecapCommentCommandService,
) : StickerApi {
    override fun getRecap(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
    ): ApiResponse<RecapDetailResponse> =
        stickerQueryService
            .getRecap(userId, stickerId)
            .let(RecapDetailResponse::from)
            .let { ApiResponse.success(it) }

    override fun updateTitle(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
        @Valid @RequestBody request: UpdateStickerTitleRequest,
    ): ApiResponse<UpdateStickerTitleResponse> =
        stickerCommandService
            .rename(userId, stickerId, request.title)
            .let(UpdateStickerTitleResponse::from)
            .let { ApiResponse.success(it) }

    override fun updateCommentPositions(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
        @Valid @RequestBody request: UpdateRecapCommentPositionsRequest,
    ): ApiResponse<Unit> =
        recapCommentCommandService
            .updatePositions(userId, stickerId, request.comments.map { it.toDomain() })
            .let { ApiResponse.success() }

    override fun delete(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
    ): ApiResponse<Unit> =
        stickerCommandService
            .delete(userId, stickerId)
            .let { ApiResponse.success() }

    override fun regenerate(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
    ): ApiResponse<RecapDetailResponse> =
        stickerCommandService
            .regenerate(userId, stickerId)
            .let { stickerQueryService.getRecap(userId, stickerId) }
            .let(RecapDetailResponse::from)
            .let { ApiResponse.success(it) }

    override fun markViewed(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
    ): ApiResponse<Unit> =
        stickerCommandService
            .markViewed(userId, stickerId)
            .let { ApiResponse.success() }
}
