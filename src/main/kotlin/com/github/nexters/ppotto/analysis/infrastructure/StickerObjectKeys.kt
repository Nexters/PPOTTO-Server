package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.global.storage.ObjectKeyGenerator
import java.util.UUID

object StickerObjectKeys {
    private const val NAMESPACE = "stickers"
    private val objectKeyGenerator = ObjectKeyGenerator()

    fun keyFor(
        analysisId: UUID,
        themeIndex: Int,
        sourcePhotoId: UUID,
    ): String = "${objectKeyGenerator.prefix(NAMESPACE, analysisId.toString())}$themeIndex-$sourcePhotoId.png"

    fun keyForRegeneration(
        stickerId: UUID,
        sourcePhotoId: UUID,
        regenerationId: UUID,
    ): String = "${objectKeyGenerator.prefix(NAMESPACE, stickerId.toString())}$sourcePhotoId-$regenerationId.png"
}
