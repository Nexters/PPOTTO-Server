package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.global.config.GcsProperties
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import org.springframework.stereotype.Service

@Service
class PhotoQueryService(
    private val photoRepository: PhotoRepository,
    private val photoStorage: PhotoStorage,
    private val gcsProperties: GcsProperties,
) {
    fun getReadablePhotos(
        analysisId: AnalysisId,
        boardId: BoardId,
        photoIds: Collection<PhotoId>,
    ): List<PhotoReadResult> =
        photoRepository
            .findCompletedByIds(analysisId.value, boardId.value, photoIds.map(PhotoId::value))
            .map { it to it.objectKey() }
            .let(::signReadUrls)

    fun getPhotoRefs(
        analysisId: AnalysisId,
        boardId: BoardId,
        photoIds: Collection<PhotoId>,
    ): List<PhotoRef> =
        photoRepository
            .findCompletedByIds(analysisId.value, boardId.value, photoIds.map(PhotoId::value))
            .map {
                PhotoRef(
                    photoId = it.id,
                    gcsUri = "gs://${gcsProperties.bucket}/${it.objectKey()}",
                    mimeType = it.contentType.mimeType,
                )
            }

    private fun signReadUrls(keyedPhotos: List<Pair<Photo, String>>): List<PhotoReadResult> =
        photoStorage
            .issueReadUrls(keyedPhotos.map { (_, objectKey) -> objectKey })
            .let { readUrls ->
                keyedPhotos.map { (photo, objectKey) ->
                    PhotoReadResult(
                        id = PhotoId(photo.id),
                        imageUrl = readUrls[objectKey] ?: error("사진 읽기 URL이 누락되었습니다."),
                        takenAt = photo.takenAt ?: error("사진 촬영 시각이 비어 있습니다."),
                        isRepresentative = photo.isRepresentative,
                    )
                }
            }

    private fun Photo.objectKey(): String = PhotoObjectKeys.keyFor(analysisId, id, contentType)
}
