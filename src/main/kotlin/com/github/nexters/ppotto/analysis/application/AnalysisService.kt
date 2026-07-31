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
    // signed URL 발급 최적화 필요 (트래픽 증가 전에 우선순위)
    // 현재: analysis/photos를 트랜잭션 내에서 저장하고, 같은 트랜잭션 내에서 signed URL을 발급한다.
    // 이유: API 응답에 analysisId와 모든 uploads를 함께 반환해야 하며 (원자성), 실패 복구용 reissue API가 아직 없다.
    // 리스크: signing 지연(credential 방식 의존) 동안 DB 커넥션을 90~100개 photo만큼 점유 → 트래픽 증가 시 병목 가능성
    // 장기 방향: (1) analysis/photos 커밋 → (2) signed URL 발급 → (3) 실패 시 POST /analysis/{id}/reissue로 복구
    // (reissue API 구현 및 analysis-status 추가 후 전환 추천)
    @Transactional
    fun createAnalysis(
        boardId: UUID,
        photos: List<PhotoUploadItemRequest>,
    ): AnalysisCreationResult {
        validatePhotoCount(photos.size)
        val board = boardQueryService.getById(boardId)
        validateNoActiveAnalysis(board.userId)
        val analysis = saveAnalysisWithConstraintFallback(board.userId, boardId)

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

    fun startUpload(analysisId: UUID): UploadVerificationResult {
        val analysis = analysisRepository.findById(analysisId) ?: throw NotFoundException()
        if (analysis.status != AnalysisStatus.UPLOADING) {
            throw ConflictException(AnalysisErrorCode.ALREADY_STARTED_OR_FINISHED)
        }

        val pendingPhotos = photoRepository.findPendingByAnalysisId(analysisId)
        if (pendingPhotos.isEmpty()) return UploadVerificationResult(0, 0, emptyList())

        val existingObjects = photoStorage.existingObjects(PhotoObjectKeys.prefixFor(analysisId))
        val completedUpdates =
            pendingPhotos
                .mapNotNull { photo ->
                    val meta = existingObjects[PhotoObjectKeys.keyFor(analysisId, photo.id, photo.contentType)]
                    if (meta != null && meta.size > 0) photo.id to meta.createdAt else null
                }.toMap()

        return transactionTemplate.execute {
            analysisRepository.findByIdForUpdate(analysisId)

            if (completedUpdates.isNotEmpty()) {
                photoRepository.updateStatusBatch(completedUpdates, UploadStatus.PENDING, UploadStatus.COMPLETED)
            }

            val (completed, pending) =
                photoRepository
                    .findAllByAnalysisId(analysisId)
                    .partition { it.uploadStatus == UploadStatus.COMPLETED }

            UploadVerificationResult(completed.size, pending.size, pending.map { it.id })
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
