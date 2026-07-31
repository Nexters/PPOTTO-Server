package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.BusinessException
import java.util.UUID

object ThemeClassificationValidator {
    const val MAX_THEME_COUNT = 6
    const val MIN_THEME_COUNT = 1

    fun validate(
        classifications: List<ThemeClassification>,
        inputPhotoIds: Set<UUID>,
    ) {
        validateThemeCount(classifications.size)
        validateNoBlankFields(classifications)
        validateCategorizedPhotoIds(classifications)
        validatePhotoIdsAreInInput(classifications, inputPhotoIds)
        validateNoDuplicatePhotoIdsBetweenThemes(classifications)
        validateStickerSourcePhotoId(classifications)
    }

    private fun validateThemeCount(count: Int) {
        if (count < MIN_THEME_COUNT || count > MAX_THEME_COUNT) {
            throw BusinessException(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                message = "테마 개수는 $MIN_THEME_COUNT 개 이상 $MAX_THEME_COUNT 개 이하여야 합니다. (실제: ${count}개)",
            )
        }
    }

    private fun validateNoBlankFields(classifications: List<ThemeClassification>) {
        for ((index, classification) in classifications.withIndex()) {
            validateThemeField(index, classification.theme)
            validateRecapBadgeField(index, classification.recap.badge)
            validateRecapTextField(index, classification.recap.text)
            validateStickerTargetSubjectField(index, classification.stickerTargetSubject)
        }
    }

    private fun validateThemeField(
        index: Int,
        theme: String,
    ) {
        if (theme.isBlank()) {
            throw BusinessException(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                message = "테마 #${index + 1}: theme이 비어있습니다.",
            )
        }
    }

    private fun validateRecapBadgeField(
        index: Int,
        badge: String,
    ) {
        if (badge.isBlank()) {
            throw BusinessException(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                message = "테마 #${index + 1}: recap.badge가 비어있습니다.",
            )
        }
    }

    private fun validateRecapTextField(
        index: Int,
        text: String,
    ) {
        if (text.isBlank()) {
            throw BusinessException(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                message = "테마 #${index + 1}: recap.text가 비어있습니다.",
            )
        }
    }

    private fun validateStickerTargetSubjectField(
        index: Int,
        subject: String,
    ) {
        if (subject.isBlank()) {
            throw BusinessException(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                message = "테마 #${index + 1}: stickerTargetSubject가 비어있습니다.",
            )
        }
    }

    private fun validateCategorizedPhotoIds(classifications: List<ThemeClassification>) {
        for ((index, classification) in classifications.withIndex()) {
            if (classification.categorizedPhotoIds.isEmpty()) {
                throw BusinessException(
                    AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                    message = "테마 #${index + 1}: categorizedPhotoIds가 비어있습니다.",
                )
            }
            val duplicateIds =
                classification.categorizedPhotoIds
                    .groupingBy { it }
                    .eachCount()
                    .filter { it.value > 1 }
                    .keys
            if (duplicateIds.isNotEmpty()) {
                throw BusinessException(
                    AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                    message = "테마 #${index + 1}: 같은 사진 ID가 여러 번 포함되었습니다. (중복: $duplicateIds)",
                )
            }
        }
    }

    private fun validatePhotoIdsAreInInput(
        classifications: List<ThemeClassification>,
        inputPhotoIds: Set<UUID>,
    ) {
        for ((index, classification) in classifications.withIndex()) {
            val unknownIds = classification.categorizedPhotoIds.toSet() - inputPhotoIds
            if (unknownIds.isNotEmpty()) {
                throw BusinessException(
                    AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                    message = "테마 #${index + 1}: 입력에 없는 사진 ID가 포함되었습니다. (알 수 없는 ID: $unknownIds)",
                )
            }
        }
    }

    private fun validateNoDuplicatePhotoIdsBetweenThemes(classifications: List<ThemeClassification>) {
        val allPhotoIds = mutableListOf<UUID>()
        for ((index, classification) in classifications.withIndex()) {
            for (photoId in classification.categorizedPhotoIds) {
                if (photoId in allPhotoIds) {
                    throw BusinessException(
                        AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                        message = "사진 ID ${photoId}이 여러 테마에 중복 분류되었습니다.",
                    )
                }
                allPhotoIds.add(photoId)
            }
        }
    }

    private fun validateStickerSourcePhotoId(classifications: List<ThemeClassification>) {
        for ((index, classification) in classifications.withIndex()) {
            if (classification.stickerSourcePhotoId !in classification.categorizedPhotoIds) {
                val themeNum = index + 1
                val sourceId = classification.stickerSourcePhotoId
                val errorMsg =
                    "테마 #$themeNum: stickerSourcePhotoId($sourceId)가 이 테마의 categorizedPhotoIds에 없습니다."
                throw BusinessException(
                    AnalysisErrorCode.INVALID_GEMINI_RESPONSE,
                    message = errorMsg,
                )
            }
        }
    }
}
