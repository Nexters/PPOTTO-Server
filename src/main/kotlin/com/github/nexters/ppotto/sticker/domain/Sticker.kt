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
    posX: Double,
    posY: Double,
    scale: Double,
    rotation: Double,
    zIndex: Int,
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
    var posX: Double = posX
        private set
    var posY: Double = posY
        private set
    var scale: Double = scale
        private set
    var rotation: Double = rotation
        private set
    var zIndex: Int = zIndex
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

    init {
        title
            .also(::validateTitle)
            .also { validateSummary(summary) }
            .let { validateContent(type, sourcePhotoId, imageKey, textContent) }
    }

    fun rename(title: String): Unit =
        title
            .also(::validateTitle)
            .let { this.title = it }

    fun markViewed(viewedAt: Instant): Unit =
        viewedAt
            .takeIf { this.viewedAt == null }
            ?.let { this.viewedAt = it }
            ?: Unit

    fun updateLayout(layout: StickerLayout): Unit =
        layout
            .also {
                posX = it.posX
                posY = it.posY
                scale = it.scale
                rotation = it.rotation
                zIndex = it.zIndex
                badgeOffsetX = it.badgeOffsetX
                badgeOffsetY = it.badgeOffsetY
                badgeRotation = it.badgeRotation
            }.let { it.title?.let(::rename) ?: Unit }

    fun regenerateSticker(
        sourcePhotoId: PhotoId,
        imageKey: String,
    ): Unit =
        validateContent(type, sourcePhotoId, imageKey, textContent)
            .let {
                this.sourcePhotoId = sourcePhotoId
                this.imageKey = imageKey
            }

    fun delete(deletedAt: Instant): Unit =
        deletedAt
            .takeIf { this.deletedAt == null }
            ?.let { this.deletedAt = it }
            ?: Unit

    companion object {
        const val MAX_TITLE_LENGTH = 15
        const val MAX_SUMMARY_LENGTH = 100

        fun validateTitle(title: String) {
            title
                .takeUnless { it.isBlank() || it.length > MAX_TITLE_LENGTH }
                ?: throw InvalidInputException()
        }

        fun validateSummary(summary: String) {
            summary
                .takeUnless { it.isBlank() || it.length > MAX_SUMMARY_LENGTH }
                ?: throw InvalidInputException()
        }

        fun validateContent(
            type: StickerType,
            sourcePhotoId: PhotoId?,
            imageKey: String?,
            textContent: String?,
        ) {
            when (type) {
                StickerType.IMAGE -> sourcePhotoId != null && !imageKey.isNullOrBlank()
                StickerType.TEXT -> !textContent.isNullOrBlank()
            }.takeIf { it }
                ?: throw InvalidInputException()
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
    val layout: StickerLayout,
) {
    init {
        title
            .also(Sticker::validateTitle)
            .also { Sticker.validateSummary(summary) }
            .let { Sticker.validateContent(type, sourcePhotoId, imageKey, textContent) }
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
