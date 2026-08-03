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
        analysisId: UUID,
        boardId: UUID,
        items: List<PhotoCreate>,
    ): List<Photo> {
        if (items.isEmpty()) return emptyList()

        var insert =
            dslContext
                .insertInto(
                    PHOTOS,
                    PHOTOS.ANALYSIS_ID,
                    PHOTOS.BOARD_ID,
                    PHOTOS.CONTENT_TYPE,
                    PHOTOS.TAKEN_AT,
                    PHOTOS.BURST_GROUP_ID,
                    PHOTOS.IS_REPRESENTATIVE,
                )
        items.forEach { item ->
            insert =
                insert.values(
                    AnalysisId(analysisId),
                    BoardId(boardId),
                    item.contentType.mimeType,
                    item.takenAt,
                    item.burstGroupId,
                    item.isRepresentative,
                )
        }
        return insert
            .returning()
            .fetch()
            .map { it.toDomain() }
    }

    fun findPendingByAnalysisId(analysisId: UUID): List<Photo> =
        dslContext
            .selectFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.eq(AnalysisId(analysisId)))
            .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.PENDING.name))
            .fetch()
            .map { it.toDomain() }

    fun findAllByAnalysisId(analysisId: UUID): List<Photo> =
        dslContext
            .selectFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.eq(AnalysisId(analysisId)))
            .fetch()
            .map { it.toDomain() }

    fun markCompletedBatch(updates: Map<UUID, Instant>): List<Photo> {
        if (updates.isEmpty()) return emptyList()

        return updates.mapNotNull { (id, uploadedAt) ->
            dslContext
                .update(PHOTOS)
                .set(PHOTOS.UPLOAD_STATUS, UploadStatus.COMPLETED.name)
                .set(PHOTOS.UPLOADED_AT, uploadedAt)
                .where(PHOTOS.ID.eq(PhotoId(id)))
                .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.PENDING.name))
                .returning()
                .fetchOne()
                ?.toDomain()
        }
    }

    fun markFailedBatch(ids: List<UUID>) {
        if (ids.isEmpty()) return

        ids.forEach { id ->
            dslContext
                .update(PHOTOS)
                .set(PHOTOS.UPLOAD_STATUS, UploadStatus.FAILED.name)
                .where(PHOTOS.ID.eq(PhotoId(id)))
                .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.PENDING.name))
                .execute()
        }
    }

    fun findCompletedByIds(
        analysisId: UUID,
        boardId: UUID,
        ids: Collection<UUID>,
    ): List<Photo> =
        ids
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { uniqueIds ->
                dslContext
                    .selectFrom(PHOTOS)
                    .where(PHOTOS.ANALYSIS_ID.eq(AnalysisId(analysisId)))
                    .and(PHOTOS.BOARD_ID.eq(BoardId(boardId)))
                    .and(PHOTOS.ID.`in`(uniqueIds.map(::PhotoId)))
                    .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.COMPLETED.name))
                    .fetch()
                    .map { it.toDomain() }
            } ?: emptyList()

    fun countOwnedByAnalysis(
        analysisId: UUID,
        boardId: UUID,
        ids: Collection<UUID>,
    ): Int =
        ids
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { uniqueIds ->
                dslContext
                    .selectCount()
                    .from(PHOTOS)
                    .where(PHOTOS.ANALYSIS_ID.eq(AnalysisId(analysisId)))
                    .and(PHOTOS.BOARD_ID.eq(BoardId(boardId)))
                    .and(PHOTOS.ID.`in`(uniqueIds.map(::PhotoId)))
                    .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.COMPLETED.name))
                    .fetchSingle(0, Int::class.java)
            } ?: 0

    fun hardDeleteAllByAnalysisIds(analysisIds: Collection<UUID>): Int =
        analysisIds
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let {
                dslContext
                    .deleteFrom(PHOTOS)
                    .where(PHOTOS.ANALYSIS_ID.`in`(it.map(::AnalysisId)))
                    .execute()
            } ?: 0

    private fun PhotosRecord.toDomain() =
        Photo(
            id = id!!.value,
            analysisId = analysisId.value,
            boardId = boardId.value,
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
