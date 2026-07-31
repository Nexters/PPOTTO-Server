package com.github.nexters.ppotto.sticker.presentation.dto

import com.github.nexters.ppotto.sticker.application.StickerItemResult
import com.github.nexters.ppotto.sticker.domain.StickerType
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "스티커 내용과 보드 배치")
data class StickerResponse(
    val id: UUID,
    val title: String,
    val isNew: Boolean,
    val type: StickerType,
    val imageUrl: String?,
    val textContent: String?,
    val posX: Double,
    val posY: Double,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int,
    val badgeOffsetX: Double,
    val badgeOffsetY: Double,
    val badgeRotation: Double,
) {
    companion object {
        fun from(result: StickerItemResult) =
            StickerResponse(
                id = result.id,
                title = result.title,
                isNew = result.isNew,
                type = result.type,
                imageUrl = result.imageUrl,
                textContent = result.textContent,
                posX = result.posX,
                posY = result.posY,
                scale = result.scale,
                rotation = result.rotation,
                zIndex = result.zIndex,
                badgeOffsetX = result.badgeOffsetX,
                badgeOffsetY = result.badgeOffsetY,
                badgeRotation = result.badgeRotation,
            )
    }
}
