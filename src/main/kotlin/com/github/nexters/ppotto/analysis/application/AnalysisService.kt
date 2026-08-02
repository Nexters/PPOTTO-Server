package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.PhotoUploadTarget
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.global.config.GcsProperties
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@Service
class AnalysisService(
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val boardAccessService: BoardAccessService,
    private val photoStorage: PhotoStorage,
    private val transactionTemplate: TransactionTemplate,
    private val gcsProperties: GcsProperties,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun createAnalysis(
        userId: UUID,
        boardId: UUID,
        photos: List<PhotoUploadItemRequest>,
    ): AnalysisCreationResult {
        validatePhotoCount(photos.size)
        boardAccessService.getOwnedByIdForUpdate(BoardId(boardId), UserId(userId))
        validateNoActiveAnalysis(userId)
        val analysis = saveAnalysisWithConstraintFallback(userId, boardId)

        val savedPhotos =
            photoRepository.saveAll(
                analysisId = analysis.id,
                boardId = boardId,
                items =
                    photos.map {
                        val contentType = PhotoContentType.fromMimeType(it.contentType)
                        PhotoCreate(contentType, it.takenAt)
                    },
            )

        val targets =
            savedPhotos.map {
                PhotoUploadTarget(PhotoObjectKeys.keyFor(analysis.id, it.id, it.contentType), it.contentType.mimeType)
            }
        val uploadUrls = photoStorage.issueUploadUrls(targets)
        check(uploadUrls.size == savedPhotos.size) {
            "issueUploadUrls가 요청한 개수(${savedPhotos.size})와 다른 개수(${uploadUrls.size})의 URL을 반환했습니다."
        }

        val uploads = savedPhotos.zip(uploadUrls) { photo, url -> PhotoUploadUrlItem(photo.id, url) }
        return AnalysisCreationResult(analysis.id, uploads)
    }

    fun startUpload(
        userId: UUID,
        analysisId: UUID,
    ): UploadVerificationResult {
        val analysis = analysisRepository.findById(analysisId) ?: throw NotFoundException(AnalysisErrorCode.ANALYSIS_NOT_FOUND)
        validateAnalysisOwner(analysis.userId, userId)
        validateUploading(analysis.status)
        return performUploadVerification(analysisId)
    }

    private fun performUploadVerification(analysisId: UUID): UploadVerificationResult {
        val pendingPhotos = photoRepository.findPendingByAnalysisId(analysisId)
        val existingObjects =
            if (pendingPhotos.isEmpty()) {
                emptyMap()
            } else {
                photoStorage.existingObjects(PhotoObjectKeys.prefixFor(analysisId))
            }

        val completedUpdates =
            pendingPhotos
                .mapNotNull { photo ->
                    val meta = existingObjects[PhotoObjectKeys.keyFor(analysisId, photo.id, photo.contentType)]
                    if (meta != null && meta.size > 0) photo.id to meta.createdAt else null
                }.toMap()

        if (completedUpdates.isEmpty()) {
            throw ConflictException(AnalysisErrorCode.NO_UPLOADED_PHOTOS)
        }

        val failedIds = pendingPhotos.map { it.id } - completedUpdates.keys

        transactionTemplate.execute {
            val locked = analysisRepository.findByIdForUpdate(analysisId)!!
            if (locked.status != AnalysisStatus.UPLOADING) {
                throw ConflictException(AnalysisErrorCode.ALREADY_STARTED_OR_FINISHED)
            }

            photoRepository.markCompletedBatch(completedUpdates)
            if (failedIds.isNotEmpty()) photoRepository.markFailedBatch(failedIds)
            analysisRepository.markAnalyzing(analysisId, Instant.now())

            val photosToPublish =
                pendingPhotos
                    .filter { it.id in completedUpdates.keys }
                    .map { photo ->
                        PhotoRef(
                            photoId = photo.id,
                            gcsUri = "gs://${gcsProperties.bucket}/${PhotoObjectKeys.keyFor(analysisId, photo.id, photo.contentType)}",
                            mimeType = photo.contentType.mimeType,
                        )
                    }
            eventPublisher.publishEvent(AnalysisStartRequestedEvent(analysisId, photosToPublish))
        }

        return UploadVerificationResult(completedUpdates.size, failedIds.size, failedIds)
    }

    private fun validateAnalysisOwner(
        analysisUserId: UUID,
        userId: UUID,
    ) {
        if (analysisUserId != userId) throw NotFoundException()
    }

    private fun validateUploading(status: AnalysisStatus) {
        if (status != AnalysisStatus.UPLOADING) {
            throw ConflictException(AnalysisErrorCode.ALREADY_STARTED_OR_FINISHED)
        }
    }

    private fun validatePhotoCount(size: Int) {
        if (size !in MIN_PHOTO_COUNT..MAX_PHOTO_COUNT) {
            throw InvalidInputException(AnalysisErrorCode.PHOTO_COUNT_OUT_OF_RANGE)
        }
    }

    private fun validateNoActiveAnalysis(userId: UUID) {
        if (analysisRepository.findActiveByUserId(userId) != null) {
            throw ConflictException(AnalysisErrorCode.ACTIVE_ANALYSIS_EXISTS)
        }
    }

    private fun saveAnalysisWithConstraintFallback(
        userId: UUID,
        boardId: UUID,
    ) = try {
        analysisRepository.save(userId = userId, boardId = boardId)
    } catch (
        @Suppress("SwallowedException")
        e: DataIntegrityViolationException,
    ) {
        throw ConflictException(AnalysisErrorCode.ACTIVE_ANALYSIS_EXISTS)
    }

    companion object {
        const val MIN_PHOTO_COUNT = 90
        const val MAX_PHOTO_COUNT = 100
    }
}
