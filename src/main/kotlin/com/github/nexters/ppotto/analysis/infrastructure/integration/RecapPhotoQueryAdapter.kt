package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Profile("!test")
class RecapPhotoQueryAdapter(
    private val photoRepository: PhotoRepository,
    private val photoStorage: PhotoStorage,
) : RecapPhotoQueryPort {
    override fun getByIds(photoIds: Collection<UUID>): List<RecapPhotoMetadata> {
        if (photoIds.isEmpty()) return emptyList()

        val photos = photoRepository.findAllByIds(photoIds)
        val photosByAnalysisId = photos.groupBy { it.analysisId }

        val allUrls = mutableMapOf<UUID, String>()
        photosByAnalysisId.forEach { (analysisId, analysisPhotos) ->
            val objectKeys =
                analysisPhotos.map { photo ->
                    PhotoObjectKeys.keyFor(analysisId, photo.id, photo.contentType)
                }
            val urls = photoStorage.issueReadUrls(objectKeys)
            analysisPhotos.zip(urls).forEach { (photo, url) ->
                allUrls[photo.id] = url
            }
        }

        return photoIds.mapNotNull { id ->
            photos.find { it.id == id }?.let { photo ->
                RecapPhotoMetadata(
                    id = photo.id,
                    imageUrl = allUrls[photo.id] ?: error("사진 읽기 URL이 누락되었습니다: $id"),
                    takenAt = photo.takenAt ?: error("사진 촬영 시간이 없습니다: $id"),
                )
            }
        }
    }
}
