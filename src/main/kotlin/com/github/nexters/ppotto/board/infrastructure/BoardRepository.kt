package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.jooq.tables.records.BoardsRecord
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class BoardRepository(
    private val dslContext: DSLContext,
) {
    fun lockCommandsByUserId(userId: UUID) {
        dslContext.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
            "board-user:$userId",
        )
    }

    fun save(
        userId: UUID,
        name: String = Board.defaultName(1),
    ): Board =
        dslContext
            .insertInto(BOARDS, BOARDS.USER_ID, BOARDS.NAME)
            .values(userId, name)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: UUID): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id))
            .and(BOARDS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun findOwnedById(
        id: UUID,
        userId: UUID,
    ): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id))
            .and(BOARDS.USER_ID.eq(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun findOwnedByIdForUpdate(
        id: UUID,
        userId: UUID,
    ): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id))
            .and(BOARDS.USER_ID.eq(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .forUpdate()
            .fetchOne()
            ?.toDomain()

    fun findByUserId(userId: UUID): List<Board> =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.USER_ID.eq(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .orderBy(BOARDS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun countByUserId(userId: UUID): Int =
        dslContext
            .fetchCount(
                dslContext
                    .selectOne()
                    .from(BOARDS)
                    .where(BOARDS.USER_ID.eq(userId))
                    .and(BOARDS.DELETED_AT.isNull),
            )

    fun updateName(
        id: UUID,
        userId: UUID,
        name: String,
    ): Board? =
        dslContext
            .update(BOARDS)
            .set(BOARDS.NAME, name)
            .where(BOARDS.ID.eq(id))
            .and(BOARDS.USER_ID.eq(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .returning()
            .fetchOne()
            ?.toDomain()

    fun softDelete(
        id: UUID,
        userId: UUID,
    ): Boolean =
        dslContext
            .update(BOARDS)
            .set(BOARDS.DELETED_AT, Instant.now())
            .where(BOARDS.ID.eq(id))
            .and(BOARDS.USER_ID.eq(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .execute() == 1

    private fun BoardsRecord.toDomain() =
        Board(
            id = id!!,
            userId = userId,
            name = name,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
