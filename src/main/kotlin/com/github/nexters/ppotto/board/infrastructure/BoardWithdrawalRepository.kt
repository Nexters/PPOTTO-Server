package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class BoardWithdrawalRepository(
    private val dslContext: DSLContext,
) {
    fun findAllIdsByUserId(userId: UUID): List<UUID> =
        dslContext
            .select(BOARDS.ID)
            .from(BOARDS)
            .where(BOARDS.USER_ID.eq(userId))
            .orderBy(BOARDS.ID)
            .fetch(BOARDS.ID)
            .filterNotNull()

    fun hardDeleteAllByUserId(userId: UUID): Int =
        dslContext
            .deleteFrom(BOARDS)
            .where(BOARDS.USER_ID.eq(userId))
            .execute()
}
