package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.application.port.ThemeClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.StickerRegenerationTarget
import com.github.nexters.ppotto.analysis.domain.StickerSubjectVerification
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.support.ResettableFake
import java.util.concurrent.CopyOnWriteArrayList

class FakeThemeClassifier :
    ThemeClassifier,
    ResettableFake {
    var failureToThrow: Throwable? = null
    var classifications: List<ThemeClassification>? = null
    var onClassify: ((List<PhotoRef>) -> List<ThemeClassification>)? = null
    var onVerify: ((PhotoRef, String) -> StickerSubjectVerification?)? = null
    var regenerationTarget: StickerRegenerationTarget? = null

    val classifiedPhotoIds: MutableList<PhotoId> = CopyOnWriteArrayList()

    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> {
        classifiedPhotoIds += photos.map { it.photoId }

        val failure = failureToThrow
        if (failure != null) throw failure

        val handler = onClassify
        if (handler != null) return handler(photos)

        return classifications ?: listOf(defaultTheme(photos))
    }

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: PhotoId,
    ): StickerRegenerationTarget {
        val failure = failureToThrow
        if (failure != null) throw failure

        return regenerationTarget ?: StickerRegenerationTarget(
            stickerTargetSubject = "재생성된 피사체",
            stickerSourcePhotoId = photos.first().photoId,
            stickerMainColor = DEFAULT_MAIN_COLOR,
        )
    }

    override fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification? {
        val handler = onVerify
        if (handler != null) return handler(photo, targetSubject)

        return StickerSubjectVerification(targetSubject = targetSubject, mainColor = DEFAULT_MAIN_COLOR)
    }

    override fun reset() {
        failureToThrow = null
        classifications = null
        onClassify = null
        onVerify = null
        regenerationTarget = null
        classifiedPhotoIds.clear()
    }

    companion object {
        const val DEFAULT_MAIN_COLOR = "#FF6B6B"

        fun defaultTheme(photos: List<PhotoRef>): ThemeClassification =
            ThemeClassification(
                theme = "테스트테마",
                categorizedPhotoIds = photos.map { it.photoId },
                recap = RecapContent(badge = "테스트뱃지", text = "테스트 리캡 문구입니다."),
                stickerTargetSubject = "테스트 피사체",
                stickerSourcePhotoId = photos.first().photoId,
                stickerMainColor = DEFAULT_MAIN_COLOR,
                comments =
                    listOf(
                        ThemeComment(content = "테스트 말풍선", posX = -96.0, posY = -150.0),
                        ThemeComment(content = "테스트 키워드", posX = null, posY = null),
                    ),
            )
    }
}
