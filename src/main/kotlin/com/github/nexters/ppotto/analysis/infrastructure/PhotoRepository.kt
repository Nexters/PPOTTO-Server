package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.jooq.tables.records.PhotosRecord
import com.github.nexters.ppotto.jooq.tables.references.PHOTOS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

data class PhotoCreate(
    val contentType: PhotoContentType,
    val takenAt: Instant,
    val burstGroupId: UUID? = null,
    val isRepresentative: Boolean = true,
)

@Repository
class PhotoRepository(
    private val dslContext: DSLContext,
) {
    fun saveAll(
        analysisId: AnalysisId,
        boardId: BoardId,
        items: List<PhotoCreate>,
    ): List<Photo> {
        if (items.isEmpty()) return emptyList()

        val insert =
            dslContext.insertInto(
                PHOTOS,
                PHOTOS.ANALYSIS_ID,
                PHOTOS.BOARD_ID,
                PHOTOS.CONTENT_TYPE,
                PHOTOS.TAKEN_AT,
                PHOTOS.BURST_GROUP_ID,
                PHOTOS.IS_REPRESENTATIVE,
            )
        return items
            .fold(insert) { statement, item ->
                statement.values(
                    analysisId,
                    boardId,
                    item.contentType.mimeType,
                    item.takenAt,
                    item.burstGroupId,
                    item.isRepresentative,
                )
            }.returning()
            .fetch()
            .map { it.toDomain() }
    }

    fun findPendingByAnalysisId(analysisId: AnalysisId): List<Photo> =
        dslContext
            .selectFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.eq(analysisId))
            .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.PENDING.name))
            .fetch()
            .map { it.toDomain() }

    fun findCompletedByAnalysisId(analysisId: AnalysisId): List<Photo> =
        dslContext
            .selectFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.eq(analysisId))
            .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.COMPLETED.name))
            .fetch()
            .map { it.toDomain() }

    fun findAllByAnalysisId(analysisId: AnalysisId): List<Photo> =
        dslContext
            .selectFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.eq(analysisId))
            .fetch()
            .map { it.toDomain() }

    fun markCompletedBatch(updates: Map<PhotoId, Instant>): Int {
        if (updates.isEmpty()) return 0

        val statements =
            updates.map { (id, uploadedAt) ->
                dslContext
                    .update(PHOTOS)
                    .set(PHOTOS.UPLOAD_STATUS, UploadStatus.COMPLETED.name)
                    .set(PHOTOS.UPLOADED_AT, uploadedAt)
                    .where(PHOTOS.ID.eq(id))
                    .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.PENDING.name))
            }
        return dslContext
            .batch(statements)
            .execute()
            .sum()
    }

    fun markFailedBatch(ids: List<PhotoId>): Int {
        if (ids.isEmpty()) return 0

        return dslContext
            .update(PHOTOS)
            .set(PHOTOS.UPLOAD_STATUS, UploadStatus.FAILED.name)
            .where(PHOTOS.ID.`in`(ids.toSet()))
            .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.PENDING.name))
            .execute()
    }

    fun markAllFailedByAnalysisId(analysisId: AnalysisId): Int =
        dslContext
            .update(PHOTOS)
            .set(PHOTOS.UPLOAD_STATUS, UploadStatus.FAILED.name)
            .where(PHOTOS.ANALYSIS_ID.eq(analysisId))
            .execute()

    fun findCompletedByIds(
        analysisId: AnalysisId,
        boardId: BoardId,
        ids: Collection<PhotoId>,
    ): List<Photo> {
        val uniqueIds = ids.toSet()
        if (uniqueIds.isEmpty()) return emptyList()

        return dslContext
            .selectFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.eq(analysisId))
            .and(PHOTOS.BOARD_ID.eq(boardId))
            .and(PHOTOS.ID.`in`(uniqueIds))
            .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.COMPLETED.name))
            .fetch()
            .map { it.toDomain() }
    }

    fun countOwnedByAnalysis(
        analysisId: AnalysisId,
        boardId: BoardId,
        ids: Collection<PhotoId>,
    ): Int {
        val uniqueIds = ids.toSet()
        if (uniqueIds.isEmpty()) return 0

        return dslContext.fetchCount(
            PHOTOS,
            PHOTOS.ANALYSIS_ID
                .eq(analysisId)
                .and(PHOTOS.BOARD_ID.eq(boardId))
                .and(PHOTOS.ID.`in`(uniqueIds))
                .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.COMPLETED.name)),
        )
    }

    fun hardDeleteAllByAnalysisIds(analysisIds: Collection<AnalysisId>): Int {
        val uniqueIds = analysisIds.toSet()
        if (uniqueIds.isEmpty()) return 0

        return dslContext
            .deleteFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.`in`(uniqueIds))
            .execute()
    }

    private fun PhotosRecord.toDomain() =
        Photo(
            id = id!!,
            analysisId = analysisId,
            boardId = boardId,
            contentType =
                PhotoContentType.entries.find { it.mimeType == contentType }
                    ?: error("unknown photos.content_type: $contentType"),
            uploadStatus = UploadStatus.valueOf(uploadStatus!!),
            uploadedAt = uploadedAt,
            takenAt = takenAt,
            burstGroupId = burstGroupId,
            isRepresentative = isRepresentative!!,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
