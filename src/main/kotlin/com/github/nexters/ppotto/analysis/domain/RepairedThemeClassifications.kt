package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.PhotoId

data class RepairedThemeClassifications(
    val classifications: List<ThemeClassification>,
    val removedPhotoCount: Int,
)

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
