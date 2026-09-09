package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import org.springframework.stereotype.Service

@Service
class PhotoQueryService(
    private val photoRepository: PhotoRepository,
    private val photoStorage: PhotoStorage,
) {
    fun getReadablePhotos(
        analysisId: AnalysisId,
        boardId: BoardId,
        photoIds: Collection<PhotoId>,
    ): List<PhotoReadResult> {
        val photos = photoRepository.findCompletedByIds(analysisId, boardId, photoIds)
        val readUrls = photoStorage.issueReadUrls(photos)

        return photos.map { photo ->
            PhotoReadResult(
                id = photo.id,
                imageUrl = readUrls[photo.id] ?: error("사진 읽기 URL이 누락되었습니다."),
                takenAt = photo.takenAt ?: error("사진 촬영 시각이 비어 있습니다."),
                isRepresentative = photo.isRepresentative,
                burstGroupId = photo.burstGroupId,
            )
        }
    }

    fun getPhotoRefs(
        analysisId: AnalysisId,
        boardId: BoardId,
        photoIds: Collection<PhotoId>,
    ): List<PhotoRef> =
        photoRepository
            .findCompletedByIds(analysisId, boardId, photoIds)
            .map { PhotoRef(photoId = it.id, sourceUri = photoStorage.sourceUri(it), mimeType = it.contentType.mimeType) }
}
