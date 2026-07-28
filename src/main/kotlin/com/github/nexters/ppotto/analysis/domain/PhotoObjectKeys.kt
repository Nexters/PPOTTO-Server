package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.storage.ObjectKeyGenerator
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PhotoObjectKeys(
    private val objectKeyGenerator: ObjectKeyGenerator,
) {
    fun prefixFor(analysisId: UUID): String = objectKeyGenerator.prefix(NAMESPACE, analysisId.toString())

    fun keyFor(
        analysisId: UUID,
        photoId: UUID,
        contentType: PhotoContentType,
    ): String = objectKeyGenerator.generate(NAMESPACE, analysisId.toString(), id = photoId, extension = contentType.extension)

    companion object {
        private const val NAMESPACE = "photos"
    }
}
