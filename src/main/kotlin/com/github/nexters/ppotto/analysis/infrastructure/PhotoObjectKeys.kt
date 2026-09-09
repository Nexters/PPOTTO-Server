package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.storage.ObjectKeyGenerator

object PhotoObjectKeys {
    private const val NAMESPACE = "photos"
    private val objectKeyGenerator = ObjectKeyGenerator()

    fun prefixFor(analysisId: AnalysisId): String = objectKeyGenerator.prefix(NAMESPACE, analysisId.toString())

    fun keyFor(
        analysisId: AnalysisId,
        photoId: PhotoId,
        contentType: PhotoContentType,
    ): String = objectKeyGenerator.generate(NAMESPACE, analysisId.toString(), id = photoId.value, extension = contentType.extension)

    fun keyFor(photo: Photo): String = keyFor(photo.analysisId, photo.id, photo.contentType)
}
