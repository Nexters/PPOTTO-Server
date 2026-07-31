package com.github.nexters.ppotto.sticker.application.port

import java.util.UUID

interface AnalysisPhotoOwnershipPort {
    fun matches(scope: AnalysisPhotoOwnershipScope): Boolean
}

data class AnalysisPhotoOwnershipScope(
    val userId: UUID,
    val boardId: UUID,
    val analysisId: UUID,
    val photoIds: Set<UUID>,
)
