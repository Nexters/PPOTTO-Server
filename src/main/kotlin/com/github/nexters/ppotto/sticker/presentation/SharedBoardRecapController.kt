package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.web.ApiResponse
import com.github.nexters.ppotto.sticker.application.SharedBoardRecapQueryService
import com.github.nexters.ppotto.sticker.presentation.dto.RecapDetailResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class SharedBoardRecapController(
    private val sharedBoardRecapQueryService: SharedBoardRecapQueryService,
) : SharedBoardRecapApi {
    override fun get(
        @PathVariable shareToken: String,
        @PathVariable stickerId: StickerId,
    ): ApiResponse<RecapDetailResponse> =
        sharedBoardRecapQueryService
            .get(shareToken, stickerId)
            .let(RecapDetailResponse::from)
            .let { ApiResponse.success(it) }
}
