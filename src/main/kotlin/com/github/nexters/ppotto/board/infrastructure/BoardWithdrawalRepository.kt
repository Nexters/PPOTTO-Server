package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.jooq.tables.references.BOARD_INVITATIONS
import com.github.nexters.ppotto.jooq.tables.references.BOARD_MEMBERS
import com.github.nexters.ppotto.jooq.tables.references.BOARD_SHARES
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
            .where(BOARDS.USER_ID.eq(userId))
            .orderBy(BOARDS.ID)
            .fetch(BOARDS.ID)
            .filterNotNull()

    fun hardDeleteMembershipsByUserId(userId: UserId): Int =
        dslContext
            .deleteFrom(BOARD_MEMBERS)
            .where(BOARD_MEMBERS.USER_ID.eq(userId))
            .execute()

    fun hardDeleteInvitationsByInviterUserId(userId: UserId): Int =
        dslContext
            .deleteFrom(BOARD_INVITATIONS)
            .where(BOARD_INVITATIONS.INVITER_USER_ID.eq(userId))
            .execute()

    fun hardDeleteSharesBySharerUserId(userId: UserId): Int =
        dslContext
            .deleteFrom(BOARD_SHARES)
            .where(BOARD_SHARES.SHARER_USER_ID.eq(userId))
            .execute()

    fun hardDeleteAllByUserId(userId: UserId): Int =
        dslContext
            .deleteFrom(BOARDS)
            .where(BOARDS.USER_ID.eq(userId))
            .execute()
}
