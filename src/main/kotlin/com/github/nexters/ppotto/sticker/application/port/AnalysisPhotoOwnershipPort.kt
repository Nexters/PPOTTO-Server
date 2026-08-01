package com.github.nexters.ppotto.sticker.application.port

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.UserId

interface AnalysisPhotoOwnershipPort {
    fun matches(scope: AnalysisPhotoOwnershipScope): Boolean
}

data class AnalysisPhotoOwnershipScope(
    val userId: UserId,
    val boardId: BoardId,
    val analysisId: AnalysisId,
    val photoIds: Set<PhotoId>,
)
