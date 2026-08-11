package com.github.nexters.ppotto.analysis.domain

import java.util.UUID

interface GeminiClassifier {
    fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification>

    fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: UUID,
    ): StickerRegenerationTarget

    fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification?
}
