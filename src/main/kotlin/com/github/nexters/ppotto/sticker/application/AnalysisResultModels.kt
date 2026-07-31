package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import java.util.UUID

data class SaveAnalysisResultCommand(
    val userId: UUID,
    val analysisId: UUID,
    val boardId: UUID,
    val stickers: List<AnalysisStickerResult>,
)

data class AnalysisStickerResult(
    val type: StickerType,
    val title: String,
    val summary: String,
    val sourcePhotoId: UUID?,
    val imageKey: String?,
    val textContent: String?,
    val layout: StickerLayout,
    val photoIds: List<UUID>,
    val comments: List<RecapCommentCreation>,
)

data class SavedAnalysisResult(
    val stickerIds: List<UUID>,
)
