package com.github.nexters.ppotto.sticker.support

import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.sticker.domain.StickerCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType

fun defaultStickerLayout(): StickerLayout =
    StickerLayout(
        posX = 1.0,
        posY = 2.0,
        scale = 1.0,
        rotation = 0.0,
        zIndex = 0,
        badgeOffsetX = 0.0,
        badgeOffsetY = 0.0,
        badgeRotation = 0.0,
    )

fun imageStickerCreation(
    sourcePhotoId: PhotoId,
    imageKey: String = "stickers/original.png",
    title: String = "원래 제목",
    summary: String = "한 줄 요약",
): StickerCreation =
    StickerCreation(
        type = StickerType.IMAGE,
        title = title,
        summary = summary,
        sourcePhotoId = sourcePhotoId,
        imageKey = imageKey,
        textContent = null,
    )

fun textStickerCreation(
    title: String = "원래 제목",
    summary: String = "한 줄 요약",
    textContent: String = "텍스트",
): StickerCreation =
    StickerCreation(
        type = StickerType.TEXT,
        title = title,
        summary = summary,
        sourcePhotoId = null,
        imageKey = null,
        textContent = textContent,
    )
