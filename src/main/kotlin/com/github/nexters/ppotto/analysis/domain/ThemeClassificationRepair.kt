package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.PhotoId

/**
 * Gemini 가 같은 사진을 여러 테마에 넣는 응답이 프로덕션 분석 실패의 최대 원인이었다.
 * 스키마는 맞고 의미만 틀린 200 응답이라 SDK 재시도 대상이 아니고, 분석 전체가 버려졌다.
 * 사진 한 장의 소속만 정하면 나머지는 그대로 쓸 수 있으므로 한 테마만 남기고 지운다.
 */
fun List<ThemeClassification>.withoutCrossThemeDuplicates(): List<ThemeClassification> {
    val ownerIndexByPhotoId = ownerIndexByPhotoId()
    return mapIndexed { index, classification ->
        val kept = classification.categorizedPhotoIds.filter { ownerIndexByPhotoId[it] == index }
        if (kept.size == classification.categorizedPhotoIds.size) {
            classification
        } else {
            classification.copy(categorizedPhotoIds = kept)
        }
    }
}

/**
 * 스티커 소스로 쓰는 테마가 먼저 나온 테마보다 우선한다. 소스 사진을 다른 테마에 넘기면
 * validateStickerSourcePhotoId 에서 어차피 실패해, 교정이 오히려 새 실패를 만든다.
 * 두 테마가 같은 사진을 소스로 지목한 응답은 교정 대상이 아니라 그대로 ANALYSIS-007 이다.
 */
private fun List<ThemeClassification>.ownerIndexByPhotoId(): Map<PhotoId, Int> {
    val firstSeen = mutableMapOf<PhotoId, Int>()
    val stickerSource = mutableMapOf<PhotoId, Int>()

    forEachIndexed { index, classification ->
        classification.categorizedPhotoIds.forEach { firstSeen.putIfAbsent(it, index) }
        if (classification.stickerSourcePhotoId in classification.categorizedPhotoIds) {
            stickerSource.putIfAbsent(classification.stickerSourcePhotoId, index)
        }
    }
    return firstSeen + stickerSource
}
