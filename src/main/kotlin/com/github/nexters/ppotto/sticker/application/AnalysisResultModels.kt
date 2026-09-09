package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.domain.StickerType

data class SaveAnalysisResultCommand(
    val userId: UserId,
    val analysisId: AnalysisId,
    val boardId: BoardId,
    val stickers: List<AnalysisStickerResult>,
) {
    init {
        if (stickers.size > Sticker.MAX_ANALYSIS_STICKER_COUNT) {
            throw InvalidInputException(StickerErrorCode.ANALYSIS_STICKER_COUNT_EXCEEDED)
        }
    }
}

data class AnalysisStickerResult(
    val type: StickerType,
    val title: String,
    val summary: String,
    val sourcePhotoId: PhotoId?,
    val imageKey: String?,
    val textContent: String?,
    val mainColor: String,
    val photoIds: List<PhotoId>,
    val comments: List<RecapCommentCreation>,
)

data class SavedAnalysisResult(
    val stickerIds: List<StickerId>,
)
