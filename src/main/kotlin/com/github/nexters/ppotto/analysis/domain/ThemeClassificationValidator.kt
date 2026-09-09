package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.PhotoId

object ThemeClassificationValidator {
    const val MAX_THEME_COUNT = 6
    const val MIN_THEME_COUNT = 1

    fun validate(
        classifications: List<ThemeClassification>,
        inputPhotoIds: Set<PhotoId>,
    ) {
        validateThemeCount(classifications.size)
        validateNoBlankFields(classifications)
        validateCategorizedPhotoIds(classifications)
        validatePhotoIdsAreInInput(classifications, inputPhotoIds)
        validateNoDuplicatePhotoIdsBetweenThemes(classifications)
        validateStickerSourcePhotoId(classifications)
    }

    private fun validateThemeCount(count: Int) {
        if (count !in MIN_THEME_COUNT..MAX_THEME_COUNT) {
            throw invalidResponse("테마 개수는 $MIN_THEME_COUNT 개 이상 $MAX_THEME_COUNT 개 이하여야 합니다. (실제: ${count}개)")
        }
    }

    private fun validateNoBlankFields(classifications: List<ThemeClassification>) {
        for ((index, classification) in classifications.withIndex()) {
            requireNotBlank(index, "theme이", classification.theme)
            requireNotBlank(index, "recap.badge가", classification.recap.badge)
            requireNotBlank(index, "recap.text가", classification.recap.text)
            requireNotBlank(index, "stickerTargetSubject가", classification.stickerTargetSubject)
        }
    }

    private fun requireNotBlank(
        index: Int,
        subject: String,
        value: String,
    ) {
        if (value.isBlank()) {
            throw invalidResponse("테마 #${index + 1}: $subject 비어있습니다.")
        }
    }

    private fun validateCategorizedPhotoIds(classifications: List<ThemeClassification>) {
        for ((index, classification) in classifications.withIndex()) {
            if (classification.categorizedPhotoIds.isEmpty()) {
                throw invalidResponse("테마 #${index + 1}: categorizedPhotoIds가 비어있습니다.")
            }
            val duplicateIds = duplicatesOf(classification.categorizedPhotoIds)
            if (duplicateIds.isNotEmpty()) {
                throw invalidResponse("테마 #${index + 1}: 같은 사진 ID가 여러 번 포함되었습니다. (중복: $duplicateIds)")
            }
        }
    }

    private fun validatePhotoIdsAreInInput(
        classifications: List<ThemeClassification>,
        inputPhotoIds: Set<PhotoId>,
    ) {
        for ((index, classification) in classifications.withIndex()) {
            val unknownIds = classification.categorizedPhotoIds.toSet() - inputPhotoIds
            if (unknownIds.isNotEmpty()) {
                throw invalidResponse("테마 #${index + 1}: 입력에 없는 사진 ID가 포함되었습니다. (알 수 없는 ID: $unknownIds)")
            }
        }
    }

    private fun validateNoDuplicatePhotoIdsBetweenThemes(classifications: List<ThemeClassification>) {
        val duplicateIds = duplicatesOf(classifications.flatMap { it.categorizedPhotoIds.distinct() })
        if (duplicateIds.isNotEmpty()) {
            throw invalidResponse("사진 ID ${duplicateIds.first()}이 여러 테마에 중복 분류되었습니다.")
        }
    }

    private fun validateStickerSourcePhotoId(classifications: List<ThemeClassification>) {
        for ((index, classification) in classifications.withIndex()) {
            if (classification.stickerSourcePhotoId !in classification.categorizedPhotoIds) {
                throw invalidResponse(
                    "테마 #${index + 1}: stickerSourcePhotoId(${classification.stickerSourcePhotoId})가 " +
                        "이 테마의 categorizedPhotoIds에 없습니다.",
                )
            }
        }
    }

    private fun duplicatesOf(photoIds: List<PhotoId>): Set<PhotoId> =
        photoIds
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys

    private fun invalidResponse(message: String) = BusinessException(AnalysisErrorCode.INVALID_GEMINI_RESPONSE, message = message)
}
