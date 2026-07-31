package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.application.AnalysisQueryService
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipPort
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipScope
import org.springframework.stereotype.Component

@Component
class StickerAnalysisPhotoOwnershipAdapter(
    private val analysisQueryService: AnalysisQueryService,
) : AnalysisPhotoOwnershipPort {
    override fun matches(scope: AnalysisPhotoOwnershipScope): Boolean =
        analysisQueryService.ownsAnalysisPhotos(
            userId = scope.userId,
            boardId = scope.boardId,
            analysisId = scope.analysisId,
            photoIds = scope.photoIds,
        )
}
