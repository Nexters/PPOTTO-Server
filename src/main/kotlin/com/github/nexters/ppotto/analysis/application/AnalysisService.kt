package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.PhotoUploadTarget
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@Service
class AnalysisService(
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val boardQueryService: BoardQueryService,
    private val photoStorage: PhotoStorage,
    private val photoObjectKeys: PhotoObjectKeys,
    private val transactionTemplate: TransactionTemplate,
) {
    @Transactional
    fun createAnalysis(
        boardId: UUID,
        photos: List<PhotoUploadItemRequest>,
    ): AnalysisCreationResult {
        val board = boardQueryService.getById(boardId)
        val analysis = analysisRepository.save(userId = board.userId, boardId = boardId)

        val savedPhotos =
            photoRepository.saveAll(
                analysisId = analysis.id,
                boardId = boardId,
                items =
                    photos.map {
                        val contentType = it.contentType?.let { raw -> PhotoContentType.fromMimeType(raw) } ?: PhotoContentType.DEFAULT
                        PhotoCreate(contentType, it.takenAt)
                    },
            )

        val targets =
            savedPhotos.map {
                PhotoUploadTarget(photoObjectKeys.keyFor(analysis.id, it.id, it.contentType), it.contentType.mimeType)
            }
        val uploadUrls = photoStorage.issueUploadUrls(targets)
        check(uploadUrls.size == savedPhotos.size) {
            "issueUploadUrls가 요청한 개수(${savedPhotos.size})와 다른 개수(${uploadUrls.size})의 URL을 반환했습니다."
        }

        val uploads = savedPhotos.zip(uploadUrls) { photo, url -> PhotoUploadUrlItem(photo.id, url) }
        return AnalysisCreationResult(analysis.id, uploads)
    }

    fun startUpload(analysisId: UUID): UploadVerificationResult {
        val analysis = analysisRepository.findById(analysisId) ?: throw NotFoundException()
        if (analysis.status != AnalysisStatus.UPLOADING) {
            throw ConflictException(AnalysisErrorCode.ALREADY_STARTED_OR_FINISHED)
        }

        val pendingPhotos = photoRepository.findPendingByAnalysisId(analysisId)
        if (pendingPhotos.isEmpty()) return UploadVerificationResult(0, 0, emptyList())

        val existingKeys = photoStorage.existingObjectKeys(photoObjectKeys.prefixFor(analysisId))
        val completedIds =
            pendingPhotos
                .filter { photoObjectKeys.keyFor(analysisId, it.id, it.contentType) in existingKeys }
                .map { it.id }

        return transactionTemplate.execute {
            analysisRepository.findByIdForUpdate(analysisId)

            if (completedIds.isNotEmpty()) {
                photoRepository.updateStatusBatch(completedIds, UploadStatus.PENDING, UploadStatus.COMPLETED, Instant.now())
            }

            val (completed, pending) =
                photoRepository
                    .findAllByAnalysisId(analysisId)
                    .partition { it.uploadStatus == UploadStatus.COMPLETED }

            UploadVerificationResult(completed.size, pending.size, pending.map { it.id })
        }
    }
}
