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
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import org.slf4j.LoggerFactory
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
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun createAnalysis(
        userId: UserId,
        boardId: BoardId,
        photoGroups: List<PhotoUploadGroupRequest>,
    ): AnalysisCreationResult {
        validateGroupCount(photoGroups.size)
        val photoCreates = resolveBurstGroups(photoGroups)

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
        val analysis = findOwnedAnalysis(analysisId, userId)
        validateUploading(analysis.status)
        return performUploadVerification(analysisId)
    }

    fun reissueUploadUrls(
        userId: UserId,
        analysisId: AnalysisId,
    ): List<PhotoUploadUrlItem> {
        val analysis = findOwnedAnalysis(analysisId, userId)
        validateUploading(analysis.status)

        val pendingPhotos = photoRepository.findPendingByAnalysisId(analysisId)
        if (pendingPhotos.isEmpty()) return emptyList()

        return issueUploadUrlItems(pendingPhotos)
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
        val startedAt = System.nanoTime()
        log.info("analysis upload verification started: analysisId={}", analysisId)
        val pendingPhotos = photoRepository.findPendingByAnalysisId(analysisId)
        log.info("analysis upload verification pending photos loaded: analysisId={}, pendingCount={}", analysisId, pendingPhotos.size)

        val uploadedObjects = photoStorage.uploadedObjects(analysisId, pendingPhotos)
        val completedUpdates =
            pendingPhotos
                .mapNotNull { photo ->
                    val meta = uploadedObjects[photo.id]
                    if (meta != null && meta.size > 0) photo.id to meta.createdAt else null
                }.toMap()

        if (completedUpdates.isEmpty()) {
            log.warn(
                "analysis upload verification failed: analysisId={}, pendingCount={}, elapsedMs={}",
                analysisId,
                pendingPhotos.size,
                elapsedMs(startedAt),
            )
            throw ConflictException(AnalysisErrorCode.NO_UPLOADED_PHOTOS)
        }

        val failedIds = pendingPhotos.map { it.id } - completedUpdates.keys
        val photoRefs = pendingPhotos.filter { it.id in completedUpdates }.map { it.toRef() }

        transactionTemplate.executeWithoutResult {
            val locked =
                checkNotNull(analysisRepository.findByIdForUpdate(analysisId)) { "분석을 찾을 수 없습니다: $analysisId" }
            if (locked.status != AnalysisStatus.UPLOADING) {
                throw ConflictException(AnalysisErrorCode.ALREADY_STARTED_OR_FINISHED)
            }

            photoRepository.markCompletedBatch(completedUpdates)
            if (failedIds.isNotEmpty()) photoRepository.markFailedBatch(failedIds)
            analysisRepository.markAnalyzing(analysisId, Instant.now())
            eventPublisher.publishEvent(AnalysisStartRequestedEvent(analysisId, photoRefs))
            log.info(
                "analysis upload verification completed: analysisId={}, uploadedCount={}, failedCount={}, elapsedMs={}",
                analysisId,
                completedUpdates.size,
                failedIds.size,
                elapsedMs(startedAt),
            )
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

    companion object {
        const val MIN_GROUP_COUNT = 20
        const val MAX_GROUP_COUNT = 100
        const val MAX_BURST_GROUP_SIZE = 10

        private val log = LoggerFactory.getLogger(AnalysisService::class.java)
    }
}

private fun validateUploading(status: AnalysisStatus) {
    if (status != AnalysisStatus.UPLOADING) {
        throw ConflictException(AnalysisErrorCode.ALREADY_STARTED_OR_FINISHED)
    }
}

private fun validateGroupCount(size: Int) {
    if (size !in AnalysisService.MIN_GROUP_COUNT..AnalysisService.MAX_GROUP_COUNT) {
        throw InvalidInputException(AnalysisErrorCode.GROUP_COUNT_OUT_OF_RANGE)
    }
}

private fun resolveBurstGroups(groups: List<PhotoUploadGroupRequest>): List<PhotoCreate> =
    groups.flatMap { group ->
        if (group.items.size > AnalysisService.MAX_BURST_GROUP_SIZE) {
            throw InvalidInputException(AnalysisErrorCode.BURST_GROUP_SIZE_EXCEEDED)
        }
        if (group.items.size == 1) {
            return@flatMap group.items.map { PhotoCreate(it.contentType, it.takenAt, burstGroupId = null, isRepresentative = true) }
        }
        if (group.items.count { it.isRepresentative } != 1) {
            throw InvalidInputException(AnalysisErrorCode.INVALID_BURST_GROUP)
        }
        val burstGroupId = UUID.randomUUID()
        group.items.map { PhotoCreate(it.contentType, it.takenAt, burstGroupId, it.isRepresentative) }
    }
