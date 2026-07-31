package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.jooq.tables.records.DrawingsRecord
import com.github.nexters.ppotto.jooq.tables.references.DRAWINGS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import java.util.UUID

@Repository
class DrawingRepository(
    private val dslContext: DSLContext,
    private val objectMapper: ObjectMapper,
) {
    fun upsertAll(drawings: List<NewDrawing>): List<Drawing> = drawings.map { upsert(it) }

    fun findByBoardId(boardId: UUID): List<Drawing> =
        dslContext
            .selectFrom(DRAWINGS)
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.DELETED_AT.isNull)
            .orderBy(DRAWINGS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun findBoardIdsByIds(ids: Collection<UUID>): Map<UUID, UUID> =
        ids
            .takeIf { it.isNotEmpty() }
            ?.let {
                dslContext
                    .select(DRAWINGS.ID, DRAWINGS.BOARD_ID)
                    .from(DRAWINGS)
                    .where(DRAWINGS.ID.`in`(it))
                    .fetch()
                    .associate { record -> record.value1()!! to record.value2()!! }
            } ?: emptyMap()

    fun findActiveIds(
        boardId: UUID,
        ids: Collection<UUID>,
    ): Set<UUID> =
        ids
            .takeIf { it.isNotEmpty() }
            ?.let {
                dslContext
                    .select(DRAWINGS.ID)
                    .from(DRAWINGS)
                    .where(DRAWINGS.BOARD_ID.eq(boardId))
                    .and(DRAWINGS.ID.`in`(it))
                    .and(DRAWINGS.DELETED_AT.isNull)
                    .mapNotNull { record -> record.value1() }
                    .toSet()
            } ?: emptySet()

    fun softDeleteByIds(
        boardId: UUID,
        ids: Collection<UUID>,
    ): Int =
        ids
            .takeIf { it.isNotEmpty() }
            ?.let {
                dslContext
                    .update(DRAWINGS)
                    .set(DRAWINGS.DELETED_AT, Instant.now())
                    .where(DRAWINGS.BOARD_ID.eq(boardId))
                    .and(DRAWINGS.ID.`in`(it))
                    .and(DRAWINGS.DELETED_AT.isNull)
                    .execute()
            } ?: 0

    fun softDeleteByStickerIds(
        boardId: UUID,
        stickerIds: Collection<UUID>,
    ): Int =
        stickerIds
            .takeIf { it.isNotEmpty() }
            ?.let {
                dslContext
                    .update(DRAWINGS)
                    .set(DRAWINGS.DELETED_AT, Instant.now())
                    .where(DRAWINGS.BOARD_ID.eq(boardId))
                    .and(DRAWINGS.STICKER_ID.`in`(it))
                    .and(DRAWINGS.DELETED_AT.isNull)
                    .execute()
            } ?: 0

    fun softDeleteAllByBoardId(boardId: UUID): Int =
        dslContext
            .update(DRAWINGS)
            .set(DRAWINGS.DELETED_AT, Instant.now())
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.DELETED_AT.isNull)
            .execute()

    private fun upsert(drawing: NewDrawing): Drawing =
        dslContext
            .insertInto(
                DRAWINGS,
                DRAWINGS.ID,
                DRAWINGS.BOARD_ID,
                DRAWINGS.STICKER_ID,
                DRAWINGS.SCOPE,
                DRAWINGS.STROKE,
                DRAWINGS.COLOR,
                DRAWINGS.STROKE_WIDTH,
            ).values(
                drawing.id,
                drawing.boardId,
                drawing.stickerId,
                drawing.scope.name,
                JSONB.jsonb(objectMapper.writeValueAsString(drawing.stroke)),
                drawing.color,
                drawing.strokeWidth,
            ).onConflict(DRAWINGS.ID)
            .doUpdate()
            .set(DRAWINGS.STICKER_ID, drawing.stickerId)
            .set(DRAWINGS.SCOPE, drawing.scope.name)
            .set(DRAWINGS.STROKE, JSONB.jsonb(objectMapper.writeValueAsString(drawing.stroke)))
            .set(DRAWINGS.COLOR, drawing.color)
            .set(DRAWINGS.STROKE_WIDTH, drawing.strokeWidth)
            .setNull(DRAWINGS.DELETED_AT)
            .where(DRAWINGS.BOARD_ID.eq(drawing.boardId))
            .returning()
            .fetchOne()!!
            .toDomain()

    private fun DrawingsRecord.toDomain() =
        Drawing(
            id = id!!,
            boardId = boardId,
            stickerId = stickerId,
            scope = DrawingScope.valueOf(scope),
            stroke = objectMapper.readValue(stroke.data()),
            color = color,
            strokeWidth = strokeWidth,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
