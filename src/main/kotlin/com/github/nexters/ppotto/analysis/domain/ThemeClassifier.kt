package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.identifier.PhotoId

interface ThemeClassifier {
    fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification>

    fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: PhotoId,
    ): StickerRegenerationTarget

    fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification?
}
