package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.storage.ObjectKeyGenerator
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StickerObjectKeys(
    private val objectKeyGenerator: ObjectKeyGenerator,
) {
    fun keyFor(
        pipelineRunId: UUID,
        theme: String,
        sourcePhotoId: UUID,
    ): String = "${objectKeyGenerator.prefix(NAMESPACE, pipelineRunId.toString(), theme)}$sourcePhotoId.png"

    companion object {
        private const val NAMESPACE = "stickers"
    }
}
