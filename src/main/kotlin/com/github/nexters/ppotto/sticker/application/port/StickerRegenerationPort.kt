package com.github.nexters.ppotto.sticker.application.port

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId

interface StickerRegenerationPort {
    fun regenerate(
        analysisId: AnalysisId,
        boardId: BoardId,
        stickerId: StickerId,
        photoIds: Collection<PhotoId>,
        previousSourcePhotoId: PhotoId,
    ): StickerRegenerationResult
}

data class StickerRegenerationResult(
    val sourcePhotoId: PhotoId,
    val imageKey: String,
    val mainColor: String,
)
