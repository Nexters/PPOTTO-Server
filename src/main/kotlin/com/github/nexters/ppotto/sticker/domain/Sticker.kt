package com.github.nexters.ppotto.sticker.domain

import com.github.nexters.ppotto.global.error.InvalidInputException
import java.time.Instant
import java.util.UUID

class Sticker(
    val id: UUID,
    val analysisId: UUID,
    val boardId: UUID,
    val type: StickerType,
    title: String,
    viewedAt: Instant?,
    val sourcePhotoId: UUID?,
    val imageKey: String?,
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

    init {
        validateTitle(title)
        validateContent(type, sourcePhotoId, imageKey, textContent)
    }

    fun rename(title: String) {
        validateTitle(title)
        this.title = title
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

    fun delete(deletedAt: Instant) {
        if (this.deletedAt == null) {
            this.deletedAt = deletedAt
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 15

        fun validateTitle(title: String) {
            if (title.isBlank() || title.length > MAX_TITLE_LENGTH) {
                throw InvalidInputException()
            }
        }

        fun validateContent(
            type: StickerType,
            sourcePhotoId: UUID?,
            imageKey: String?,
            textContent: String?,
        ) {
            val valid =
                when (type) {
                    StickerType.IMAGE -> sourcePhotoId != null && !imageKey.isNullOrBlank()
                    StickerType.TEXT -> !textContent.isNullOrBlank()
                }
            if (!valid) {
                throw InvalidInputException()
            }
        }
    }
}

data class StickerCreation(
    val type: StickerType,
    val title: String,
    val sourcePhotoId: UUID?,
    val imageKey: String?,
    val textContent: String?,
    val layout: StickerLayout,
) {
    init {
        Sticker.validateTitle(title)
        Sticker.validateContent(type, sourcePhotoId, imageKey, textContent)
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
