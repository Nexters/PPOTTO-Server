package com.github.nexters.ppotto.image.infrastructure

import com.github.nexters.ppotto.image.domain.Image
import com.github.nexters.ppotto.image.domain.UploadStatus
import com.github.nexters.ppotto.jooq.tables.records.ImagesRecord
import com.github.nexters.ppotto.jooq.tables.references.IMAGES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ImageRepository(
    private val dslContext: DSLContext,
) {
    fun save(
        boardId: UUID,
        uploadSessionId: UUID,
        originalFileName: String,
    ): Image =
        dslContext
            .insertInto(IMAGES, IMAGES.BOARD_ID, IMAGES.UPLOAD_SESSION_ID, IMAGES.ORIGINAL_FILE_NAME)
            .values(boardId, uploadSessionId, originalFileName)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun updateStatus(
        id: UUID,
        expectedStatus: UploadStatus,
        newStatus: UploadStatus,
    ): Image? =
        dslContext
            .update(IMAGES)
            .set(IMAGES.UPLOAD_STATUS, newStatus.name)
            .where(IMAGES.ID.eq(id))
            .and(IMAGES.UPLOAD_STATUS.eq(expectedStatus.name))
            .returning()
            .fetchOne()
            ?.toDomain()

    fun findById(id: UUID): Image? =
        dslContext
            .selectFrom(IMAGES)
            .where(IMAGES.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    fun findByBoardId(boardId: UUID): List<Image> =
        dslContext
            .selectFrom(IMAGES)
            .where(IMAGES.BOARD_ID.eq(boardId))
            .fetch()
            .map { it.toDomain() }

    private fun ImagesRecord.toDomain() =
        Image(
            id = id!!,
            boardId = boardId,
            uploadStatus = UploadStatus.valueOf(uploadStatus!!),
            uploadSessionId = uploadSessionId,
            originalFileName = originalFileName,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
