package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.Photo
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.jooq.tables.records.PhotosRecord
import com.github.nexters.ppotto.jooq.tables.references.PHOTOS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

data class PhotoCreate(
    val contentType: PhotoContentType,
    val takenAt: Instant?,
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
                .insertInto(PHOTOS, PHOTOS.ANALYSIS_ID, PHOTOS.BOARD_ID, PHOTOS.CONTENT_TYPE, PHOTOS.TAKEN_AT)
        items.forEach { item ->
            insert = insert.values(analysisId, boardId, item.contentType.mimeType, item.takenAt)
        }
        return insert
            .returning()
            .fetch()
            .map { it.toDomain() }
    }

    fun findPendingByAnalysisId(analysisId: UUID): List<Photo> =
        dslContext
            .selectFrom(PHOTOS)
            .where(PHOTOS.ANALYSIS_ID.eq(analysisId))
            .and(PHOTOS.UPLOAD_STATUS.eq(UploadStatus.PENDING.name))
            .fetch()
            .map { it.toDomain() }

    fun updateStatusBatch(
        ids: List<UUID>,
        expectedStatus: UploadStatus,
        newStatus: UploadStatus,
        uploadedAt: Instant?,
    ): List<Photo> {
        if (ids.isEmpty()) return emptyList()

        return dslContext
            .update(PHOTOS)
            .set(PHOTOS.UPLOAD_STATUS, newStatus.name)
            .set(PHOTOS.UPLOADED_AT, uploadedAt)
            .where(PHOTOS.ID.`in`(ids))
            .and(PHOTOS.UPLOAD_STATUS.eq(expectedStatus.name))
            .returning()
            .fetch()
            .map { it.toDomain() }
    }

    private fun PhotosRecord.toDomain() =
        Photo(
            id = id!!,
            analysisId = analysisId,
            boardId = boardId,
            contentType = PhotoContentType.fromMimeType(contentType),
            uploadStatus = UploadStatus.valueOf(uploadStatus!!),
            uploadedAt = uploadedAt,
            takenAt = takenAt,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
