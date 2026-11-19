package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.sticker.domain.Sticker
import org.jooq.DSLContext
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

    fun updateLayout(
        sticker: Sticker,
        title: String?,
        expectedLayoutVersion: Long?,
    ): Boolean {
        val update =
            dslContext
                .update(STICKERS)
                .set(STICKERS.POS_X, sticker.posX)
                .set(STICKERS.POS_Y, sticker.posY)
                .set(STICKERS.SCALE, sticker.scale)
                .set(STICKERS.ROTATION, sticker.rotation)
                .set(STICKERS.Z_INDEX, sticker.zIndex)
                .set(STICKERS.BADGE_OFFSET_X, sticker.badgeOffsetX)
                .set(STICKERS.BADGE_OFFSET_Y, sticker.badgeOffsetY)
                .set(STICKERS.BADGE_ROTATION, sticker.badgeRotation)
                .set(STICKERS.LAYOUT_VERSION, STICKERS.LAYOUT_VERSION.plus(1L))
        if (title != null) update.set(STICKERS.TITLE, sticker.title)
        val condition =
            STICKERS.ID
                .eq(sticker.id)
                .and(STICKERS.DELETED_AT.isNull)
                .let { if (expectedLayoutVersion == null) it else it.and(STICKERS.LAYOUT_VERSION.eq(expectedLayoutVersion)) }
        return update.where(condition).execute() == 1
    }

    fun existsActiveById(stickerId: StickerId): Boolean =
        dslContext
            .selectOne()
            .from(STICKERS)
            .where(STICKERS.ID.eq(stickerId))
            .and(STICKERS.DELETED_AT.isNull)
            .fetchOne() != null

    fun findDeletedIds(
        boardId: BoardId,
        stickerIds: Set<StickerId>,
    ): Set<StickerId> {
        if (stickerIds.isEmpty()) return emptySet()
        return dslContext
            .select(STICKERS.ID)
            .from(STICKERS)
            .where(STICKERS.BOARD_ID.eq(boardId))
            .and(STICKERS.ID.`in`(stickerIds))
            .and(STICKERS.DELETED_AT.isNotNull)
            .fetch(STICKERS.ID)
            .mapTo(mutableSetOf()) { checkNotNull(it) }
    }

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
            .set(STICKERS.MAIN_COLOR, sticker.mainColor)
            .where(STICKERS.ID.eq(sticker.id))
            .and(STICKERS.DELETED_AT.isNull)
            .execute() == 1

    fun updateShare(
        stickerId: StickerId,
        shareToken: String,
        sharePhotos: Boolean,
    ): Boolean =
        dslContext
            .update(STICKERS)
            .set(STICKERS.SHARE_TOKEN, shareToken)
            .set(STICKERS.SHARE_PHOTOS, sharePhotos)
            .where(STICKERS.ID.eq(stickerId))
            .and(STICKERS.DELETED_AT.isNull)
            .execute() == 1

    fun clearShare(stickerId: StickerId): Boolean =
        dslContext
            .update(STICKERS)
            .setNull(STICKERS.SHARE_TOKEN)
            .set(STICKERS.SHARE_PHOTOS, false)
            .where(STICKERS.ID.eq(stickerId))
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

    fun hardDeleteByIds(stickerIds: Collection<StickerId>) {
        dslContext
            .deleteFrom(STICKERS)
            .where(STICKERS.ID.`in`(stickerIds.toSet()))
            .execute()
    }
}
