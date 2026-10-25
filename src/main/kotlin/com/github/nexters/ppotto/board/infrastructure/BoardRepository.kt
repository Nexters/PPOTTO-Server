package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.records.BoardsRecord
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.jooq.tables.references.BOARD_MEMBERS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant

data class BoardWithMemberCount(
    val board: Board,
    val memberCount: Int,
)

@Repository
class BoardRepository(
    private val dslContext: DSLContext,
) {
    fun save(
        userId: UserId,
        name: String = Board.defaultName(1),
    ): Board =
        dslContext
            .insertInto(BOARDS, BOARDS.USER_ID, BOARDS.NAME)
            .values(userId, name)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: BoardId): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id))
            .and(BOARDS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun findOwnedById(
        id: BoardId,
        userId: UserId,
    ): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id))
            .and(BOARDS.USER_ID.eq(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun findReadableById(
        id: BoardId,
        userId: UserId,
    ): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id))
            .and(ownedOrJoinedBy(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun findOwnedByIdForUpdate(
        id: BoardId,
        userId: UserId,
    ): Board? =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.ID.eq(id))
            .and(BOARDS.USER_ID.eq(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .forUpdate()
            .fetchOne()
            ?.toDomain()

    fun findByUserId(userId: UserId): List<Board> =
        dslContext
            .selectFrom(BOARDS)
            .where(BOARDS.USER_ID.eq(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .orderBy(BOARDS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun findAccessibleByUserId(userId: UserId): List<BoardWithMemberCount> {
        val memberCount =
            DSL.field(
                DSL
                    .selectCount()
                    .from(BOARD_MEMBERS)
                    .where(BOARD_MEMBERS.BOARD_ID.eq(BOARDS.ID)),
            )
        return dslContext
            .select(BOARDS, memberCount)
            .from(BOARDS)
            .where(ownedOrJoinedBy(userId))
            .and(BOARDS.DELETED_AT.isNull)
            .fetch { BoardWithMemberCount(it.value1().toDomain(), it.value2()) }
    }

    fun countByUserId(userId: UserId): Int =
        dslContext
            .fetchCount(
                dslContext
                    .selectOne()
                    .from(BOARDS)
                    .where(BOARDS.USER_ID.eq(userId))
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
            .where(BOARDS.ID.eq(id))
            .and(BOARDS.USER_ID.eq(userId))
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

private fun ownedOrJoinedBy(userId: UserId): Condition =
    BOARDS.USER_ID.eq(userId).or(
        BOARDS.ID.`in`(
            DSL
                .select(BOARD_MEMBERS.BOARD_ID)
                .from(BOARD_MEMBERS)
                .where(BOARD_MEMBERS.USER_ID.eq(userId)),
        ),
    )
