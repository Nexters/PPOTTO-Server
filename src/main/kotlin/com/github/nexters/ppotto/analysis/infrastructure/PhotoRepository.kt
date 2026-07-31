package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.jooq.tables.records.PhotosRecord
import com.github.nexters.ppotto.jooq.tables.references.PHOTOS
import org.jooq.DSLContext
import org.jooq.impl.DSL.row
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

data class PhotoCreate(
    val contentType: PhotoContentType,
    val takenAt: Instant,
)

@Repository
class PhotoRepository(
    private val dslContext: DSLContext,
) {
    fun saveAll(
        analysisId: UUID,
        boardId: UUID,
        items: List<PhotoCreate>,
    ): List<Photo> =
        items
            .takeIf { it.isNotEmpty() }
            ?.let { photos ->
                dslContext
                    .insertInto(PHOTOS, PHOTOS.ANALYSIS_ID, PHOTOS.BOARD_ID, PHOTOS.CONTENT_TYPE, PHOTOS.TAKEN_AT)
                    .valuesOfRows(
                        photos.map {
                            row(analysisId, boardId, it.contentType.mimeType, it.takenAt)
                        },
                    ).returning()
                    .fetch()
                    .map { it.toDomain() }
            } ?: emptyList()

    fun findPendingByAnalysisId(analysisId: UUID): List<Photo> =
        dslContext
            .selectFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.eq(analysisId))
            .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.PENDING.name))
            .fetch()
            .map { it.toDomain() }

    fun findAllByAnalysisId(analysisId: UUID): List<Photo> =
        dslContext
            .selectFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.eq(analysisId))
            .fetch()
            .map { it.toDomain() }

    fun updateStatusBatch(
        updates: Map<UUID, Instant>,
        expectedStatus: UploadStatus,
        newStatus: UploadStatus,
    ): List<Photo> =
        updates
            .takeIf { it.isNotEmpty() }
            ?.mapNotNull { (id, uploadedAt) ->
                dslContext
                    .update(PHOTOS)
                    .set(PHOTOS.UPLOAD_STATUS, newStatus.name)
                    .set(PHOTOS.UPLOADED_AT, uploadedAt)
                    .where(PHOTOS.ID.eq(id))
                    .and(PHOTOS.UPLOAD_STATUS.eq(expectedStatus.name))
                    .returning()
                    .fetchOne()
                    ?.toDomain()
            } ?: emptyList()

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
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
