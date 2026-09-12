package com.github.nexters.ppotto.analysis.infrastructure.storage

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.storage.ObjectKeyGenerator
import java.util.UUID

object StickerObjectKeys {
    private const val NAMESPACE = "stickers"
    private val objectKeyGenerator = ObjectKeyGenerator()

    fun keyFor(
        analysisId: AnalysisId,
        themeIndex: Int,
        sourcePhotoId: PhotoId,
    ): String = "${objectKeyGenerator.prefix(NAMESPACE, analysisId.toString())}$themeIndex-$sourcePhotoId.png"

    fun keyForRegeneration(
        stickerId: StickerId,
        sourcePhotoId: PhotoId,
        regenerationId: UUID,
    ): String = "${objectKeyGenerator.prefix(NAMESPACE, stickerId.toString())}$sourcePhotoId-$regenerationId.png"
}
