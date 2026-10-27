package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.BoardInvitation
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.records.BoardInvitationsRecord
import com.github.nexters.ppotto.jooq.tables.references.BOARD_INVITATIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class BoardInvitationRepository(
    private val dslContext: DSLContext,
) {
    fun save(
        boardId: BoardId,
        inviterUserId: UserId,
        code: String,
        expiresAt: Instant,
    ): BoardInvitation =
        dslContext
            .insertInto(
                BOARD_INVITATIONS,
                BOARD_INVITATIONS.BOARD_ID,
                BOARD_INVITATIONS.INVITER_USER_ID,
                BOARD_INVITATIONS.CODE,
                BOARD_INVITATIONS.EXPIRES_AT,
            ).values(boardId, inviterUserId, code, expiresAt)
            .returning()
            .fetchOne()!!
            .toDomain()

    fun findById(id: BoardInvitationId): BoardInvitation? =
        dslContext
            .selectFrom(BOARD_INVITATIONS)
            .where(BOARD_INVITATIONS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    fun findByCode(code: String): BoardInvitation? =
        dslContext
            .selectFrom(BOARD_INVITATIONS)
            .where(BOARD_INVITATIONS.CODE.eq(code))
            .fetchOne()
            ?.toDomain()

    fun findByBoardIdAndInviterUserId(
        boardId: BoardId,
        inviterUserId: UserId,
    ): BoardInvitation? =
        dslContext
            .selectFrom(BOARD_INVITATIONS)
            .where(BOARD_INVITATIONS.BOARD_ID.eq(boardId))
            .and(BOARD_INVITATIONS.INVITER_USER_ID.eq(inviterUserId))
            .fetchOne()
            ?.toDomain()

    fun reissue(
        id: BoardInvitationId,
        code: String,
        expiresAt: Instant,
    ): BoardInvitation =
        dslContext
            .update(BOARD_INVITATIONS)
            .set(BOARD_INVITATIONS.CODE, code)
            .set(BOARD_INVITATIONS.EXPIRES_AT, expiresAt)
            .setNull(BOARD_INVITATIONS.PREVIEW_IMAGE_KEY)
            .where(BOARD_INVITATIONS.ID.eq(id))
            .returning()
            .fetchOne()!!
            .toDomain()

    private fun BoardInvitationsRecord.toDomain() =
        BoardInvitation(
            id = id!!,
            boardId = boardId,
            inviterUserId = inviterUserId,
            code = code,
            expiresAt = expiresAt,
            previewImageKey = previewImageKey,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
