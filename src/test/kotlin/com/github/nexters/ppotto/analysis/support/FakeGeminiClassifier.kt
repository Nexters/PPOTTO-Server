package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.ThemeClassification

class FakeGeminiClassifier : GeminiClassifier {
    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> =
        listOf(
            ThemeClassification(
                theme = "테스트테마",
                categorizedPhotoIds = photos.map { it.photoId },
                recap = RecapContent(badge = "테스트뱃지", text = "테스트 리캡 문구입니다."),
                stickerTargetSubject = "테스트 피사체",
                stickerSourcePhotoId = photos.first().photoId,
            ),
        )
}
