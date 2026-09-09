package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.Analysis
import com.github.nexters.ppotto.analysis.domain.AnalysisCanceledEvent
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStartRequestedEvent
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Service
class AnalysisService(
    private val analysisRepository: AnalysisRepository,
    private val photoRepository: PhotoRepository,
    private val boardAccessService: BoardAccessService,
    private val photoStorage: PhotoStorage,
    private val transactionTemplate: TransactionTemplate,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun createAnalysis(
        userId: UserId,
        boardId: BoardId,
        command: CreateAnalysisCommand,
    ): AnalysisCreationResult {
        val photoCreates = command.toPhotoCreates()
        val savedPhotos =
            checkNotNull(
                transactionTemplate.execute { savePendingPhotos(userId, boardId, photoCreates) },
            ) { "분석 생성 트랜잭션이 결과를 반환하지 않았습니다." }

        return AnalysisCreationResult(savedPhotos.first().analysisId, issueUploadUrlItems(savedPhotos))
    }

    fun startUpload(
        userId: UserId,
        analysisId: AnalysisId,
    ): UploadVerificationResult {
        findOwnedAnalysis(analysisId, userId).requireUploading()
        return performUploadVerification(analysisId)
    }

    fun reissueUploadUrls(
        userId: UserId,
        analysisId: AnalysisId,
    ): List<PhotoUploadUrlItem> {
        findOwnedAnalysis(analysisId, userId).requireUploading()

        return photoRepository
            .findPendingByAnalysisId(analysisId)
            .takeIf { it.isNotEmpty() }
            ?.let(::issueUploadUrlItems)
            ?: emptyList()
    }

    @Transactional
    fun cancelAnalysis(
        userId: UserId,
        analysisId: AnalysisId,
    ) {
        val locked = analysisRepository.findByIdForUpdate(analysisId)
        if (locked == null || locked.userId != userId) throw NotFoundException(AnalysisErrorCode.ANALYSIS_NOT_FOUND)
        if (locked.status != AnalysisStatus.UPLOADING) {
            throw ConflictException(AnalysisErrorCode.CANCEL_NOT_ALLOWED)
        }

        analysisRepository.markFailed(analysisId, AnalysisRepository.FAILED_REASON_CANCELED)
        photoRepository.markAllFailedByAnalysisId(analysisId)
        eventPublisher.publishEvent(AnalysisCanceledEvent(analysisId))
    }

    private fun savePendingPhotos(
        userId: UserId,
        boardId: BoardId,
        photoCreates: List<PhotoCreate>,
    ): List<Photo> {
        boardAccessService.getOwnedByIdForUpdate(boardId, userId)
        validateNoActiveAnalysis(userId)
        val analysis = saveAnalysisWithConstraintFallback(userId, boardId)
        return photoRepository.saveAll(analysisId = analysis.id, boardId = boardId, items = photoCreates)
    }

    private fun issueUploadUrlItems(photos: List<Photo>): List<PhotoUploadUrlItem> {
        val uploadUrls = photoStorage.issueUploadUrls(photos)
        return photos.map { photo ->
            PhotoUploadUrlItem(photo.id, checkNotNull(uploadUrls[photo.id]) { "업로드 URL이 발급되지 않았습니다: ${photo.id}" })
        }
    }

    private fun performUploadVerification(analysisId: AnalysisId): UploadVerificationResult {
        val pendingPhotos = photoRepository.findPendingByAnalysisId(analysisId)
        val uploadedObjects = photoStorage.uploadedObjects(analysisId, pendingPhotos)
        val completedUpdates =
            pendingPhotos
                .mapNotNull { photo ->
                    val meta = uploadedObjects[photo.id]
                    if (meta != null && meta.size > 0) photo.id to meta.createdAt else null
                }.toMap()
        if (completedUpdates.isEmpty()) throw ConflictException(AnalysisErrorCode.NO_UPLOADED_PHOTOS)

        val failedIds = pendingPhotos.map { it.id } - completedUpdates.keys
        val photoRefs = pendingPhotos.filter { it.id in completedUpdates }.map { it.toRef() }

        transactionTemplate.executeWithoutResult {
            checkNotNull(analysisRepository.findByIdForUpdate(analysisId)) { "분석을 찾을 수 없습니다: $analysisId" }.requireUploading()

            photoRepository.markCompletedBatch(completedUpdates)
            if (failedIds.isNotEmpty()) photoRepository.markFailedBatch(failedIds)
            analysisRepository.markAnalyzing(analysisId, Instant.now())
            eventPublisher.publishEvent(AnalysisStartRequestedEvent(analysisId, photoRefs))
        }

        return UploadVerificationResult(completedUpdates.size, failedIds.size, failedIds)
    }

    private fun Photo.toRef(): PhotoRef =
        PhotoRef(
            photoId = id,
            sourceUri = photoStorage.sourceUri(this),
            mimeType = contentType.mimeType,
            burstGroupId = burstGroupId,
            isRepresentative = isRepresentative,
        )

    private fun findOwnedAnalysis(
        analysisId: AnalysisId,
        userId: UserId,
    ): Analysis =
        analysisRepository.findByIdAndUserId(analysisId, userId)
            ?: throw NotFoundException(AnalysisErrorCode.ANALYSIS_NOT_FOUND)

    private fun validateNoActiveAnalysis(userId: UserId) {
        if (analysisRepository.findActiveByUserId(userId) != null) {
            throw ConflictException(AnalysisErrorCode.ACTIVE_ANALYSIS_EXISTS)
        }
    }

    private fun saveAnalysisWithConstraintFallback(
        userId: UserId,
        boardId: BoardId,
    ): Analysis =
        try {
            analysisRepository.save(userId = userId, boardId = boardId)
        } catch (
            @Suppress("SwallowedException")
            e: DataIntegrityViolationException,
        ) {
            throw ConflictException(AnalysisErrorCode.ACTIVE_ANALYSIS_EXISTS)
        }
}
