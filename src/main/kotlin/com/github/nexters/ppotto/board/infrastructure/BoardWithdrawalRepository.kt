package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class BoardWithdrawalRepository(
    private val dslContext: DSLContext,
) {
    fun findAllIdsByUserId(userId: UserId): List<BoardId> =
        dslContext
            .select(BOARDS.ID)
            .from(BOARDS)
            .where(BOARDS.USER_ID.eq(userId.value))
            .orderBy(BOARDS.ID)
            .fetch(BOARDS.ID)
            .filterNotNull()
            .map(::BoardId)

    fun hardDeleteAllByUserId(userId: UserId): Int =
        dslContext
            .deleteFrom(BOARDS)
            .where(BOARDS.USER_ID.eq(userId.value))
            .execute()
}
