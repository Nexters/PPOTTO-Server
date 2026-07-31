package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.PhotoUploadTarget
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class AnalysisService(
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val boardQueryService: BoardQueryService,
    private val photoStorage: PhotoStorage,
    private val transactionTemplate: TransactionTemplate,
) {
    @Transactional
    fun createAnalysis(
        userId: UUID,
        boardId: UUID,
        photos: List<PhotoUploadItemRequest>,
    ): AnalysisCreationResult =
        photos
            .also {
                validatePhotoCount(it.size)
                boardQueryService.getOwnedById(boardId, userId)
                validateNoActiveAnalysis(userId)
            }.let { requests ->
                saveAnalysisWithConstraintFallback(userId, boardId).let { analysis ->
                    requests
                        .map {
                            PhotoContentType
                                .fromMimeType(it.contentType)
                                .let { contentType -> PhotoCreate(contentType, it.takenAt) }
                        }.let {
                            photoRepository.saveAll(
                                analysisId = analysis.id,
                                boardId = boardId,
                                items = it,
                            )
                        }.let { savedPhotos ->
                            savedPhotos
                                .map {
                                    PhotoUploadTarget(
                                        PhotoObjectKeys.keyFor(analysis.id, it.id, it.contentType),
                                        it.contentType.mimeType,
                                    )
                                }.let(photoStorage::issueUploadUrls)
                                .also {
                                    check(it.size == savedPhotos.size) {
                                        "issueUploadUrls가 요청한 개수(${savedPhotos.size})와 다른 개수(${it.size})의 URL을 반환했습니다."
                                    }
                                }.let { uploadUrls ->
                                    savedPhotos.zip(uploadUrls) { photo, url -> PhotoUploadUrlItem(photo.id, url) }
                                }.let { AnalysisCreationResult(analysis.id, it) }
                        }
                }
            }

    fun startUpload(
        userId: UUID,
        analysisId: UUID,
    ): UploadVerificationResult =
        (analysisRepository.findById(analysisId) ?: throw NotFoundException())
            .also {
                validateAnalysisOwner(it.userId, userId)
                validateUploading(it.status)
            }.let { photoRepository.findPendingByAnalysisId(analysisId) }
            .takeIf { it.isNotEmpty() }
            ?.let { pendingPhotos ->
                photoStorage
                    .existingObjects(PhotoObjectKeys.prefixFor(analysisId))
                    .let { existingObjects ->
                        pendingPhotos
                            .mapNotNull { photo ->
                                existingObjects[PhotoObjectKeys.keyFor(analysisId, photo.id, photo.contentType)]
                                    ?.takeIf { it.size > 0 }
                                    ?.let { photo.id to it.createdAt }
                            }.toMap()
                    }.let { completedUpdates ->
                        transactionTemplate.execute {
                            analysisRepository.findByIdForUpdate(analysisId)
                            completedUpdates
                                .takeIf { it.isNotEmpty() }
                                ?.let {
                                    photoRepository.updateStatusBatch(
                                        it,
                                        UploadStatus.PENDING,
                                        UploadStatus.COMPLETED,
                                    )
                                }
                            photoRepository
                                .findAllByAnalysisId(analysisId)
                                .partition { it.uploadStatus == UploadStatus.COMPLETED }
                                .let { (completed, pending) ->
                                    UploadVerificationResult(completed.size, pending.size, pending.map { it.id })
                                }
                        }
                    }
            } ?: UploadVerificationResult(0, 0, emptyList())

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
        if (size !in 90..100) {
            throw InvalidInputException(AnalysisErrorCode.PHOTO_COUNT_OUT_OF_RANGE)
        }
    }

    private fun validateNoActiveAnalysis(userId: UUID) {
        if (analysisRepository.existsActiveByUserId(userId)) {
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
}
