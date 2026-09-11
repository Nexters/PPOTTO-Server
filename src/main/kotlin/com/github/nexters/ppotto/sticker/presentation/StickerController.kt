package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.web.ApiResponse
import com.github.nexters.ppotto.sticker.application.RecapCommentCommandService
import com.github.nexters.ppotto.sticker.application.RecapShareService
import com.github.nexters.ppotto.sticker.application.StickerCommandService
import com.github.nexters.ppotto.sticker.application.StickerQueryService
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import com.github.nexters.ppotto.sticker.presentation.dto.ShareRecapRequest
import com.github.nexters.ppotto.sticker.presentation.dto.ShareRecapResponse
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
    private val recapShareService: RecapShareService,
) : StickerApi {
    override fun getRecap(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
    ): ApiResponse<RecapDetailResponse> =
        stickerQueryService
            .getRecap(userId, stickerId)
            .let(RecapDetailResponse::from)
            .let { ApiResponse.success(it) }

    override fun getSharedRecap(
        @PathVariable shareToken: String,
    ): ApiResponse<RecapDetailResponse> =
        stickerQueryService
            .getSharedRecap(shareToken)
            .let(RecapDetailResponse::from)
            .let { ApiResponse.success(it) }

    override fun share(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
        @Valid @RequestBody request: ShareRecapRequest,
    ): ApiResponse<ShareRecapResponse> =
        recapShareService
            .share(userId, stickerId, request.includePhotos)
            .let { ApiResponse.success(ShareRecapResponse(it, request.includePhotos)) }

    override fun unshare(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
    ): ApiResponse<Unit> =
        recapShareService
            .unshare(userId, stickerId)
            .let { ApiResponse.success() }

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
    ): ApiResponse<RecapDetailResponse> {
        stickerCommandService.regenerate(userId, stickerId)
        return stickerQueryService
            .getRecap(userId, stickerId)
            .let(RecapDetailResponse::from)
            .let { ApiResponse.success(it) }
    }

    override fun markViewed(
        @AuthenticatedUser userId: UserId,
        @PathVariable stickerId: StickerId,
    ): ApiResponse<Unit> =
        stickerCommandService
            .markViewed(userId, stickerId)
            .let { ApiResponse.success() }
}
