package com.github.nexters.ppotto.sticker.support

import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipPort
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipScope
import java.util.UUID

class FakeAnalysisPhotoOwnershipPort : AnalysisPhotoOwnershipPort {
    private val allowedScopes = mutableListOf<AnalysisPhotoOwnershipScope>()

    fun allow(
        userId: UUID,
        boardId: UUID,
        analysisId: UUID,
        photoIds: Collection<UUID>,
    ) {
        allowedScopes += AnalysisPhotoOwnershipScope(userId, boardId, analysisId, photoIds.toSet())
    }

    override fun matches(scope: AnalysisPhotoOwnershipScope): Boolean =
        allowedScopes.any { allowed ->
            allowed.userId == scope.userId &&
                allowed.boardId == scope.boardId &&
                allowed.analysisId == scope.analysisId &&
                allowed.photoIds.containsAll(scope.photoIds)
        }
}
