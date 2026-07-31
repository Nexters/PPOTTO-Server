package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipPort
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipScope
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class AnalysisPhotoOwnershipAdapter(
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
) : AnalysisPhotoOwnershipPort {
    override fun matches(scope: AnalysisPhotoOwnershipScope): Boolean {
        val analysis = analysisRepository.findById(scope.analysisId) ?: return false
        if (analysis.userId != scope.userId || analysis.boardId != scope.boardId) return false

        if (scope.photoIds.isEmpty()) return true

        val photoIds =
            photoRepository
                .findAllByAnalysisId(scope.analysisId)
                .map { it.id }
                .toSet()
        return scope.photoIds.all { it in photoIds }
    }
}
