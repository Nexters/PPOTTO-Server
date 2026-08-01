package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.jooq.tables.records.DrawingsRecord
import com.github.nexters.ppotto.jooq.tables.references.DRAWINGS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL.excluded
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant

@Repository
class DrawingRepository(
    private val dslContext: DSLContext,
    private val objectMapper: ObjectMapper,
) {
    fun upsertAll(drawings: List<NewDrawing>): List<Drawing> {
        if (drawings.isEmpty()) return emptyList()

        var insert =
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
                )
        drawings.forEach { drawing ->
            insert =
                insert.values(
                    drawing.id,
                    drawing.boardId,
                    drawing.stickerId,
                    drawing.scope.name,
                    JSONB.jsonb(objectMapper.writeValueAsString(drawing.stroke)),
                    drawing.color,
                    drawing.strokeWidth,
                )
        }
        return insert
            .onConflict(DRAWINGS.ID)
            .doUpdate()
            .set(DRAWINGS.STICKER_ID, excluded(DRAWINGS.STICKER_ID))
            .set(DRAWINGS.SCOPE, excluded(DRAWINGS.SCOPE))
            .set(DRAWINGS.STROKE, excluded(DRAWINGS.STROKE))
            .set(DRAWINGS.COLOR, excluded(DRAWINGS.COLOR))
            .set(DRAWINGS.STROKE_WIDTH, excluded(DRAWINGS.STROKE_WIDTH))
            .setNull(DRAWINGS.DELETED_AT)
            .where(DRAWINGS.BOARD_ID.eq(excluded(DRAWINGS.BOARD_ID)))
            .returning()
            .fetch()
            .map { it.toDomain() }
            .associateBy { it.id }
            .let { upserted -> drawings.map { drawing -> upserted.getValue(drawing.id) } }
    }

    fun findByBoardId(boardId: BoardId): List<Drawing> =
        dslContext
            .selectFrom(DRAWINGS)
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.DELETED_AT.isNull)
            .orderBy(DRAWINGS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun findBoardIdsByIds(ids: Collection<DrawingId>): Map<DrawingId, BoardId> =
        ids
            .takeIf { it.isNotEmpty() }
            ?.let { drawingIds ->
                dslContext
                    .select(DRAWINGS.ID, DRAWINGS.BOARD_ID)
                    .from(DRAWINGS)
                    .where(DRAWINGS.ID.`in`(drawingIds))
                    .fetch()
                    .associate { record -> record.value1()!! to record.value2()!! }
            } ?: emptyMap()

    fun findActiveIds(
        boardId: BoardId,
        ids: Collection<DrawingId>,
    ): Set<DrawingId> =
        ids
            .takeIf { it.isNotEmpty() }
            ?.let { drawingIds ->
                dslContext
                    .select(DRAWINGS.ID)
                    .from(DRAWINGS)
                    .where(DRAWINGS.BOARD_ID.eq(boardId))
                    .and(DRAWINGS.ID.`in`(drawingIds))
                    .and(DRAWINGS.DELETED_AT.isNull)
                    .mapNotNull { record -> record.value1() }
                    .toSet()
            } ?: emptySet()

    fun softDeleteByIds(
        boardId: BoardId,
        ids: Collection<DrawingId>,
    ): Int =
        ids
            .takeIf { it.isNotEmpty() }
            ?.let { drawingIds ->
                dslContext
                    .update(DRAWINGS)
                    .set(DRAWINGS.DELETED_AT, Instant.now())
                    .where(DRAWINGS.BOARD_ID.eq(boardId))
                    .and(DRAWINGS.ID.`in`(drawingIds))
                    .and(DRAWINGS.DELETED_AT.isNull)
                    .execute()
            } ?: 0

    fun softDeleteByStickerIds(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    ): Int =
        stickerIds
            .takeIf { it.isNotEmpty() }
            ?.let { ids ->
                dslContext
                    .update(DRAWINGS)
                    .set(DRAWINGS.DELETED_AT, Instant.now())
                    .where(DRAWINGS.BOARD_ID.eq(boardId))
                    .and(DRAWINGS.STICKER_ID.`in`(ids))
                    .and(DRAWINGS.DELETED_AT.isNull)
                    .execute()
            } ?: 0

    fun softDeleteAllByBoardId(boardId: BoardId): Int =
        dslContext
            .update(DRAWINGS)
            .set(DRAWINGS.DELETED_AT, Instant.now())
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.DELETED_AT.isNull)
            .execute()

    fun hardDeleteAllByBoardIds(boardIds: Collection<BoardId>): Int =
        boardIds
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { ids ->
                dslContext
                    .deleteFrom(DRAWINGS)
                    .where(DRAWINGS.BOARD_ID.`in`(ids))
                    .execute()
            } ?: 0

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
