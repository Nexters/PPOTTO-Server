package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.BoardShare
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.records.BoardSharesRecord
import com.github.nexters.ppotto.jooq.tables.references.BOARD_SHARES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class BoardShareRepository(
    private val dslContext: DSLContext,
) {
    fun save(
        boardId: BoardId,
        sharerUserId: UserId,
        shareToken: String,
        scope: BoardShareScope,
    ): BoardShare =
        dslContext
            .insertInto(
                BOARD_SHARES,
                BOARD_SHARES.BOARD_ID,
                BOARD_SHARES.SHARER_USER_ID,
                BOARD_SHARES.SHARE_TOKEN,
                BOARD_SHARES.SCOPE,
            ).values(boardId, sharerUserId, shareToken, scope.name)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findByShareToken(shareToken: String): BoardShare? =
        dslContext
            .selectFrom(BOARD_SHARES)
            .where(BOARD_SHARES.SHARE_TOKEN.eq(shareToken))
            .fetchOne()
            ?.toDomain()

    fun findByBoardIdAndSharerUserIdForUpdate(
        boardId: BoardId,
        sharerUserId: UserId,
    ): BoardShare? =
        dslContext
            .selectFrom(BOARD_SHARES)
            .where(BOARD_SHARES.BOARD_ID.eq(boardId))
            .and(BOARD_SHARES.SHARER_USER_ID.eq(sharerUserId))
            .forUpdate()
            .fetchOne()
            ?.toDomain()

    fun updateScope(
        boardId: BoardId,
        sharerUserId: UserId,
        scope: BoardShareScope,
    ): BoardShare =
        dslContext
            .update(BOARD_SHARES)
            .set(BOARD_SHARES.SCOPE, scope.name)
            .where(BOARD_SHARES.BOARD_ID.eq(boardId))
            .and(BOARD_SHARES.SHARER_USER_ID.eq(sharerUserId))
            .returning()
            .fetchOne()!!
            .toDomain()

    private fun BoardSharesRecord.toDomain() =
        BoardShare(
            id = id!!,
            boardId = boardId,
            sharerUserId = sharerUserId,
            shareToken = shareToken,
            scope = BoardShareScope.valueOf(scope),
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
