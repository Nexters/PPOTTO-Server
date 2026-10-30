package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.jooq.tables.references.BOARD_MEMBERS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

data class BoardWithMemberCount(
    val board: Board,
    val memberCount: Int,
)

@Repository
class BoardMembershipRepository(
    private val dslContext: DSLContext,
) {
    fun findReadableById(
        id: BoardId,
        userId: UserId,
    ): Board? = findReadableById(id, userId, forUpdate = false)

    fun findReadableByIdForUpdate(
        id: BoardId,
        userId: UserId,
    ): Board? = findReadableById(id, userId, forUpdate = true)

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

    fun findMemberUserIds(boardId: BoardId): List<UserId> =
        dslContext
            .select(BOARD_MEMBERS.USER_ID)
            .from(BOARD_MEMBERS)
            .where(BOARD_MEMBERS.BOARD_ID.eq(boardId))
            .orderBy(BOARD_MEMBERS.CREATED_AT, BOARD_MEMBERS.ID)
            .fetch { requireNotNull(it[BOARD_MEMBERS.USER_ID]) }

    fun exists(
        boardId: BoardId,
        userId: UserId,
    ): Boolean =
        dslContext.fetchExists(
            BOARD_MEMBERS,
            BOARD_MEMBERS.BOARD_ID
                .eq(boardId)
                .and(BOARD_MEMBERS.USER_ID.eq(userId)),
        )

    fun countMembersByBoardId(boardId: BoardId): Int =
        dslContext.fetchCount(
            BOARD_MEMBERS,
            BOARD_MEMBERS.BOARD_ID.eq(boardId),
        )

    fun saveIfAbsent(
        boardId: BoardId,
        userId: UserId,
    ): Boolean =
        dslContext
            .insertInto(BOARD_MEMBERS, BOARD_MEMBERS.BOARD_ID, BOARD_MEMBERS.USER_ID)
            .values(boardId, userId)
            .onConflict(BOARD_MEMBERS.BOARD_ID, BOARD_MEMBERS.USER_ID)
            .doNothing()
            .execute() == 1

    private fun findReadableById(
        id: BoardId,
        userId: UserId,
        forUpdate: Boolean,
    ): Board? {
        val query =
            dslContext
                .selectFrom(BOARDS)
                .where(BOARDS.ID.eq(id))
                .and(ownedOrJoinedBy(userId))
                .and(BOARDS.DELETED_AT.isNull)
        return if (forUpdate) {
            query
                .forUpdate()
                .fetchOne()
                ?.toDomain()
        } else {
            query
                .fetchOne()
                ?.toDomain()
        }
    }
}

private val OWNED_BOARDS = BOARDS.`as`("owned_boards")

private fun ownedOrJoinedBy(userId: UserId): Condition =
    BOARDS.ID.`in`(
        DSL
            .select(BOARD_MEMBERS.BOARD_ID)
            .from(BOARD_MEMBERS)
            .where(BOARD_MEMBERS.USER_ID.eq(userId))
            .unionAll(
                DSL
                    .select(OWNED_BOARDS.ID)
                    .from(OWNED_BOARDS)
                    .where(OWNED_BOARDS.USER_ID.eq(userId)),
            ),
    )
