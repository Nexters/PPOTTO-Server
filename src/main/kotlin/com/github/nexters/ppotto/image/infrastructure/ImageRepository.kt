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
    ): Image =
        dslContext
            .insertInto(IMAGES, IMAGES.BOARD_ID, IMAGES.UPLOAD_SESSION_ID)
            .values(boardId, uploadSessionId)
            .returning()
            .fetchOne()!!
            .toDomain()

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
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
