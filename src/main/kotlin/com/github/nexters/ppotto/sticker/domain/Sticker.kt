package com.github.nexters.ppotto.sticker.domain

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import java.time.Instant

class Sticker(
    val id: StickerId,
    val analysisId: AnalysisId,
    val boardId: BoardId,
    val type: StickerType,
    title: String,
    val summary: String,
    viewedAt: Instant?,
    sourcePhotoId: PhotoId?,
    imageKey: String?,
    val textContent: String?,
    mainColor: String,
    posX: Double?,
    posY: Double?,
    scale: Double,
    rotation: Double,
    zIndex: Int?,
    badgeOffsetX: Double,
    badgeOffsetY: Double,
    badgeRotation: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
    deletedAt: Instant?,
) {
    var title: String = title
        private set
    var viewedAt: Instant? = viewedAt
        private set
    var posX: Double? = posX
        private set
    var posY: Double? = posY
        private set
    var scale: Double = scale
        private set
    var rotation: Double = rotation
        private set
    var zIndex: Int? = zIndex
        private set
    var badgeOffsetX: Double = badgeOffsetX
        private set
    var badgeOffsetY: Double = badgeOffsetY
        private set
    var badgeRotation: Double = badgeRotation
        private set
    var deletedAt: Instant? = deletedAt
        private set
    var sourcePhotoId: PhotoId? = sourcePhotoId
        private set
    var imageKey: String? = imageKey
        private set
    var mainColor: String = mainColor
        private set

    init {
        require(isValidTitle(title)) { "스티커 제목이 저장 규칙을 벗어났습니다: $title" }
        require(isValidSummary(summary)) { "스티커 한 줄 요약이 저장 규칙을 벗어났습니다: $summary" }
        require(isValidMainColor(mainColor)) { "스티커 메인 컬러가 hex 형식이 아닙니다: $mainColor" }
        require(hasContent(type, sourcePhotoId, imageKey, textContent)) { "$type 스티커의 내용이 비어 있습니다." }
    }

    fun rename(title: String) {
        this.title = title.takeIf { isValidTitle(it) } ?: throw InvalidInputException()
    }

    fun markViewed(viewedAt: Instant) {
        if (this.viewedAt == null) {
            this.viewedAt = viewedAt
        }
    }

    fun updateLayout(layout: StickerLayout) {
        posX = layout.posX
        posY = layout.posY
        scale = layout.scale
        rotation = layout.rotation
        zIndex = layout.zIndex
        badgeOffsetX = layout.badgeOffsetX
        badgeOffsetY = layout.badgeOffsetY
        badgeRotation = layout.badgeRotation
        layout.title?.let(::rename)
    }

    fun regenerateSticker(
        sourcePhotoId: PhotoId,
        imageKey: String,
        mainColor: String,
    ) {
        require(isValidMainColor(mainColor)) { "재생성된 스티커 메인 컬러가 hex 형식이 아닙니다: $mainColor" }
        require(hasContent(type, sourcePhotoId, imageKey, textContent)) { "재생성된 $type 스티커의 내용이 비어 있습니다." }
        this.sourcePhotoId = sourcePhotoId
        this.imageKey = imageKey
        this.mainColor = mainColor
    }

    fun delete(deletedAt: Instant) {
        if (this.deletedAt == null) {
            this.deletedAt = deletedAt
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 15
        const val MAX_SUMMARY_LENGTH = 100
        const val MAX_ANALYSIS_STICKER_COUNT = 6
        private val MAIN_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")

        fun isValidTitle(title: String): Boolean = title.isNotBlank() && title.length <= MAX_TITLE_LENGTH

        fun isValidSummary(summary: String): Boolean = summary.isNotBlank() && summary.length <= MAX_SUMMARY_LENGTH

        fun isValidMainColor(mainColor: String): Boolean = MAIN_COLOR_PATTERN.matches(mainColor)

        fun hasContent(
            type: StickerType,
            sourcePhotoId: PhotoId?,
            imageKey: String?,
            textContent: String?,
        ): Boolean =
            when (type) {
                StickerType.IMAGE -> sourcePhotoId != null && !imageKey.isNullOrBlank()
                StickerType.TEXT -> !textContent.isNullOrBlank()
            }
    }
}

data class StickerCreation(
    val type: StickerType,
    val title: String,
    val summary: String,
    val sourcePhotoId: PhotoId?,
    val imageKey: String?,
    val textContent: String?,
    val mainColor: String,
) {
    init {
        if (!Sticker.isValidTitle(title)) {
            throw InvalidInputException()
        }
        if (!Sticker.isValidSummary(summary)) {
            throw InvalidInputException()
        }
        if (!Sticker.isValidMainColor(mainColor)) {
            throw InvalidInputException()
        }
        if (!Sticker.hasContent(type, sourcePhotoId, imageKey, textContent)) {
            throw InvalidInputException()
        }
    }
}

data class StickerLayout(
    val title: String? = null,
    val posX: Double,
    val posY: Double,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int,
    val badgeOffsetX: Double,
    val badgeOffsetY: Double,
    val badgeRotation: Double,
)
