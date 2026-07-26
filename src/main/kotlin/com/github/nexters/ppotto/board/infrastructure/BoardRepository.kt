package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class BoardRepository(
    private val dslContext: DSLContext,
) {
    fun save(userId: UUID): Board =
        dslContext
            .insertInto(BOARDS, BOARDS.USER_ID)
            .values(userId)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: UUID): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    fun findByUserId(userId: UUID): List<Board> =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.USER_ID.eq(userId))
            .fetch()
            .map { it.toDomain() }

    private fun com.github.nexters.ppotto.jooq.tables.records.BoardsRecord.toDomain() =
        Board(
            id = id!!,
            userId = userId,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
