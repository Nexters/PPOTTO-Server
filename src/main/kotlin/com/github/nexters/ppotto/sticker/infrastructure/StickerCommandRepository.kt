package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.sticker.domain.Sticker
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class StickerCommandRepository(
    private val dslContext: DSLContext,
) {
    fun updateTitle(
        stickerId: StickerId,
        title: String,
    ): Boolean =
        dslContext
            .update(STICKERS)
            .set(STICKERS.TITLE, title)
            .where(STICKERS.ID.eq(stickerId))
            .and(STICKERS.DELETED_AT.isNull)
            .execute() == 1

    fun markViewed(
        stickerId: StickerId,
        viewedAt: Instant,
    ): Boolean =
        dslContext
            .update(STICKERS)
            .set(STICKERS.VIEWED_AT, DSL.coalesce(STICKERS.VIEWED_AT, viewedAt))
            .where(STICKERS.ID.eq(stickerId))
            .and(STICKERS.DELETED_AT.isNull)
            .execute() == 1

    fun updateLayout(sticker: Sticker): Boolean =
        dslContext
            .update(STICKERS)
            .set(STICKERS.TITLE, sticker.title)
            .set(STICKERS.POS_X, sticker.posX)
            .set(STICKERS.POS_Y, sticker.posY)
            .set(STICKERS.SCALE, sticker.scale)
            .set(STICKERS.ROTATION, sticker.rotation)
            .set(STICKERS.Z_INDEX, sticker.zIndex)
            .set(STICKERS.BADGE_OFFSET_X, sticker.badgeOffsetX)
            .set(STICKERS.BADGE_OFFSET_Y, sticker.badgeOffsetY)
            .set(STICKERS.BADGE_ROTATION, sticker.badgeRotation)
            .where(STICKERS.ID.eq(sticker.id))
            .and(STICKERS.DELETED_AT.isNull)
            .execute() == 1

    fun tryClaimRegenerationLock(
        stickerId: StickerId,
        now: Instant,
        lockUntil: Instant,
    ): Boolean =
        dslContext
            .update(STICKERS)
            .set(STICKERS.REGENERATION_LOCKED_UNTIL, lockUntil)
            .where(STICKERS.ID.eq(stickerId))
            .and(STICKERS.DELETED_AT.isNull)
            .and(
                STICKERS.REGENERATION_LOCKED_UNTIL.isNull
                    .or(STICKERS.REGENERATION_LOCKED_UNTIL.lt(now)),
            ).execute() == 1

    fun releaseRegenerationLock(stickerId: StickerId): Boolean =
        dslContext
            .update(STICKERS)
            .setNull(STICKERS.REGENERATION_LOCKED_UNTIL)
            .where(STICKERS.ID.eq(stickerId))
            .execute() == 1

    fun updateStickerImage(sticker: Sticker): Boolean =
        dslContext
            .update(STICKERS)
            .set(STICKERS.SOURCE_PHOTO_ID, sticker.sourcePhotoId)
            .set(STICKERS.IMAGE_KEY, sticker.imageKey)
            .where(STICKERS.ID.eq(sticker.id))
            .and(STICKERS.DELETED_AT.isNull)
            .execute() == 1

    fun softDelete(
        stickerId: StickerId,
        deletedAt: Instant,
    ): Boolean =
        dslContext
            .update(STICKERS)
            .set(STICKERS.DELETED_AT, deletedAt)
            .where(STICKERS.ID.eq(stickerId))
            .and(STICKERS.DELETED_AT.isNull)
            .execute() == 1

    fun hardDeleteByIds(stickerIds: Collection<StickerId>): Int =
        stickerIds
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { uniqueIds ->
                dslContext
                    .deleteFrom(STICKERS)
                    .where(STICKERS.ID.`in`(uniqueIds))
                    .execute()
            } ?: 0
}
