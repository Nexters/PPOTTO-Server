package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.jooq.tables.references.STICKER_VIEWS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class StickerViewRepository(
    private val dslContext: DSLContext,
) {
    fun saveIfAbsent(
        stickerId: StickerId,
        userId: UserId,
    ): Boolean =
        dslContext
            .insertInto(STICKER_VIEWS, STICKER_VIEWS.STICKER_ID, STICKER_VIEWS.USER_ID)
            .select(
                dslContext
                    .select(STICKERS.ID, DSL.`val`(userId, STICKER_VIEWS.USER_ID.dataType))
                    .from(STICKERS)
                    .where(STICKERS.ID.eq(stickerId))
                    .and(STICKERS.DELETED_AT.isNull),
            ).onConflict(STICKER_VIEWS.STICKER_ID, STICKER_VIEWS.USER_ID)
            .doNothing()
            .execute() == 1

    fun existsActiveSticker(stickerId: StickerId): Boolean =
        dslContext.fetchExists(
            STICKERS,
            STICKERS.ID
                .eq(stickerId)
                .and(STICKERS.DELETED_AT.isNull),
        )

    fun findViewedStickerIds(
        userId: UserId,
        stickerIds: Collection<StickerId>,
    ): Set<StickerId> {
        if (stickerIds.isEmpty()) return emptySet()
        return dslContext
            .select(STICKER_VIEWS.STICKER_ID)
            .from(STICKER_VIEWS)
            .where(STICKER_VIEWS.USER_ID.eq(userId))
            .and(STICKER_VIEWS.STICKER_ID.`in`(stickerIds))
            .fetch(STICKER_VIEWS.STICKER_ID)
            .filterNotNull()
            .toSet()
    }

    fun deleteAllByBoardAndUser(
        boardId: BoardId,
        userId: UserId,
    ): Int =
        dslContext
            .deleteFrom(STICKER_VIEWS)
            .where(STICKER_VIEWS.USER_ID.eq(userId))
            .and(
                STICKER_VIEWS.STICKER_ID.`in`(
                    dslContext
                        .select(STICKERS.ID)
                        .from(STICKERS)
                        .where(STICKERS.BOARD_ID.eq(boardId)),
                ),
            ).execute()

    fun deleteAllByUser(userId: UserId): Int =
        dslContext
            .deleteFrom(STICKER_VIEWS)
            .where(STICKER_VIEWS.USER_ID.eq(userId))
            .execute()
}
