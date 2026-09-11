package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.PhotoId
import org.slf4j.LoggerFactory

data class RepairedThemeClassifications(
    val classifications: List<ThemeClassification>,
    val removedPhotoCount: Int,
)

fun List<ThemeClassification>.cappedToMaxThemeCount(): List<ThemeClassification> {
    if (size <= ThemeClassificationValidator.MAX_THEME_COUNT) return this

    log.warn(
        "Gemini가 최대 테마 개수를 초과해 반환하여 앞에서부터 {}개만 사용합니다: 반환={}개",
        ThemeClassificationValidator.MAX_THEME_COUNT,
        size,
    )
    return take(ThemeClassificationValidator.MAX_THEME_COUNT)
}

fun List<ThemeClassification>.repairCrossThemeDuplicates(): RepairedThemeClassifications {
    val ownerIndexByPhotoId = ownerIndexByPhotoId()
    val repaired =
        mapIndexed { index, classification ->
            classification.copy(
                categorizedPhotoIds = classification.categorizedPhotoIds.filter { ownerIndexByPhotoId[it] == index },
            )
        }

    return RepairedThemeClassifications(
        classifications = repaired,
        removedPhotoCount = sumOf { it.categorizedPhotoIds.size } - repaired.sumOf { it.categorizedPhotoIds.size },
    )
}

private fun List<ThemeClassification>.ownerIndexByPhotoId(): Map<PhotoId, Int> {
    val firstSeenOwner = mutableMapOf<PhotoId, Int>()
    val stickerSourceOwner = mutableMapOf<PhotoId, Int>()

    forEachIndexed { index, classification ->
        classification.categorizedPhotoIds.forEach { firstSeenOwner.putIfAbsent(it, index) }
        if (classification.stickerSourcePhotoId in classification.categorizedPhotoIds) {
            stickerSourceOwner.putIfAbsent(classification.stickerSourcePhotoId, index)
        }
    }
    return firstSeenOwner + stickerSourceOwner
}

private val log = LoggerFactory.getLogger("com.github.nexters.ppotto.analysis.domain.ThemeClassifications")
