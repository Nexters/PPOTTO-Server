package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.records.BoardsRecord
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class BoardRepository(
    private val dslContext: DSLContext,
) {
    fun lockCommandsByUserId(userId: UserId) {
        dslContext.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
            "board-user:$userId",
        )
    }

    fun save(
        userId: UserId,
        name: String = Board.defaultName(1),
    ): Board =
        dslContext
            .insertInto(BOARDS, BOARDS.USER_ID, BOARDS.NAME)
            .values(userId.value, name)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: BoardId): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id.value))
            .and(BOARDS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun findOwnedById(
        id: BoardId,
        userId: UserId,
    ): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id.value))
            .and(BOARDS.USER_ID.eq(userId.value))
            .and(BOARDS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun findOwnedByIdForUpdate(
        id: BoardId,
        userId: UserId,
    ): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id.value))
            .and(BOARDS.USER_ID.eq(userId.value))
            .and(BOARDS.DELETED_AT.isNull)
            .forUpdate()
            .fetchOne()
            ?.toDomain()

    fun findByUserId(userId: UserId): List<Board> =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.USER_ID.eq(userId.value))
            .and(BOARDS.DELETED_AT.isNull)
            .orderBy(BOARDS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun countByUserId(userId: UserId): Int =
        dslContext
            .fetchCount(
                dslContext
                    .selectOne()
                    .from(BOARDS)
                    .where(BOARDS.USER_ID.eq(userId.value))
                    .and(BOARDS.DELETED_AT.isNull),
            )

    fun updateName(
        id: BoardId,
        userId: UserId,
        name: String,
    ): Board? =
        dslContext
            .update(BOARDS)
            .set(BOARDS.NAME, name)
            .where(BOARDS.ID.eq(id.value))
            .and(BOARDS.USER_ID.eq(userId.value))
            .and(BOARDS.DELETED_AT.isNull)
            .returning()
            .fetchOne()
            ?.toDomain()

    fun softDelete(
        id: BoardId,
        userId: UserId,
    ): Boolean =
        dslContext
            .update(BOARDS)
            .set(BOARDS.DELETED_AT, Instant.now())
            .where(BOARDS.ID.eq(id.value))
            .and(BOARDS.USER_ID.eq(userId.value))
            .and(BOARDS.DELETED_AT.isNull)
            .execute() == 1

    private fun BoardsRecord.toDomain() =
        Board(
            id = BoardId(id!!),
            userId = UserId(userId),
            name = name,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
