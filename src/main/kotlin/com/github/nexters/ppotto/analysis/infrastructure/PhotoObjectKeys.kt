package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.global.storage.ObjectKeyGenerator
import java.util.UUID

object PhotoObjectKeys {
    private const val NAMESPACE = "photos"
    private val objectKeyGenerator = ObjectKeyGenerator()

    fun prefixFor(analysisId: UUID): String = objectKeyGenerator.prefix(NAMESPACE, analysisId.toString())

    fun keyFor(
        analysisId: UUID,
        photoId: UUID,
        contentType: PhotoContentType,
    ): String = objectKeyGenerator.generate(NAMESPACE, analysisId.toString(), id = photoId, extension = contentType.extension)
}
