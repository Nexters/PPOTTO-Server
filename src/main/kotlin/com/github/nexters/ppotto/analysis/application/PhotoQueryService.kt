package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PhotoQueryService(
    private val photoRepository: PhotoRepository,
    private val photoStorage: PhotoStorage,
) {
    fun getReadablePhotos(
        analysisId: UUID,
        boardId: UUID,
        photoIds: Collection<UUID>,
    ): List<PhotoReadResult> =
        photoRepository
            .findCompletedByIds(analysisId, boardId, photoIds)
            .map { it to it.objectKey() }
            .let(::signReadUrls)

    private fun signReadUrls(keyedPhotos: List<Pair<Photo, String>>): List<PhotoReadResult> =
        photoStorage
            .issueReadUrls(keyedPhotos.map { (_, objectKey) -> objectKey })
            .let { readUrls ->
                keyedPhotos.map { (photo, objectKey) ->
                    PhotoReadResult(
                        id = photo.id,
                        imageUrl = readUrls[objectKey] ?: error("사진 읽기 URL이 누락되었습니다."),
                        takenAt = photo.takenAt ?: error("사진 촬영 시각이 비어 있습니다."),
                    )
                }
            }

    private fun Photo.objectKey(): String = PhotoObjectKeys.keyFor(analysisId, id, contentType)
}
