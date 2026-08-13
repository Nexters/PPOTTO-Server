package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.jooq.tables.records.RecapCommentsRecord
import com.github.nexters.ppotto.jooq.tables.references.RECAP_COMMENTS
import com.github.nexters.ppotto.jooq.tables.references.STICKER_PHOTOS
import com.github.nexters.ppotto.sticker.domain.RecapComment
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.RecapCommentPosition
import com.github.nexters.ppotto.sticker.domain.StickerPhoto
import org.jooq.DSLContext
import org.jooq.impl.DSL.row
import org.springframework.stereotype.Repository

@Repository
class StickerRecapRepository(
    private val dslContext: DSLContext,
) {
    fun savePhotos(
        stickerId: StickerId,
        photoIds: List<PhotoId>,
    ): List<StickerPhoto> =
        photoIds
            .takeIf { it.isNotEmpty() }
            ?.let { ids ->
                dslContext
                    .insertInto(STICKER_PHOTOS, STICKER_PHOTOS.STICKER_ID, STICKER_PHOTOS.PHOTO_ID)
                    .valuesOfRows(ids.map { row(stickerId, it) })
                    .returning()
                    .fetch()
                    .map { StickerPhoto(it.id!!, it.stickerId, it.photoId) }
            } ?: emptyList()

    fun saveComments(
        stickerId: StickerId,
        creations: List<RecapCommentCreation>,
    ): List<RecapComment> =
        creations
            .takeIf { it.isNotEmpty() }
            ?.let { comments ->
                dslContext
                    .insertInto(
                        RECAP_COMMENTS,
                        RECAP_COMMENTS.STICKER_ID,
                        RECAP_COMMENTS.CONTENT,
                        RECAP_COMMENTS.POS_X,
                        RECAP_COMMENTS.POS_Y,
                    ).valuesOfRows(
                        comments.map {
                            row(stickerId, it.content, it.posX, it.posY)
                        },
                    ).returning()
                    .fetch()
                    .map { it.toDomain() }
            } ?: emptyList()

    fun findPhotoIds(stickerId: StickerId): List<PhotoId> =
        dslContext
            .select(STICKER_PHOTOS.PHOTO_ID)
            .from(STICKER_PHOTOS)
            .where(STICKER_PHOTOS.STICKER_ID.eq(stickerId))
            .fetch(STICKER_PHOTOS.PHOTO_ID)
            .filterNotNull()

    fun updatePositions(
        stickerId: StickerId,
        positions: List<RecapCommentPosition>,
    ): Int =
        positions.count { position ->
            dslContext
                .update(RECAP_COMMENTS)
                .set(RECAP_COMMENTS.POS_X, position.posX)
                .set(RECAP_COMMENTS.POS_Y, position.posY)
                .where(RECAP_COMMENTS.ID.eq(position.id))
                .and(RECAP_COMMENTS.STICKER_ID.eq(stickerId))
                .and(RECAP_COMMENTS.POS_X.isNotNull)
                .execute() == 1
        }

    fun findComments(stickerId: StickerId): List<RecapComment> =
        dslContext
            .selectFrom(RECAP_COMMENTS)
            .where(RECAP_COMMENTS.STICKER_ID.eq(stickerId))
            .orderBy(RECAP_COMMENTS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun deleteByStickerIds(stickerIds: Collection<StickerId>) {
        stickerIds
            .takeIf { it.isNotEmpty() }
            ?.let { ids ->
                dslContext
                    .deleteFrom(RECAP_COMMENTS)
                    .where(RECAP_COMMENTS.STICKER_ID.`in`(ids))
                    .execute()
                    .let {
                        dslContext
                            .deleteFrom(STICKER_PHOTOS)
                            .where(STICKER_PHOTOS.STICKER_ID.`in`(ids))
                            .execute()
                    }
            }
    }

    private fun RecapCommentsRecord.toDomain() =
        RecapComment(
            id = id!!,
            stickerId = stickerId,
            content = content,
            posX = posX,
            posY = posY,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
