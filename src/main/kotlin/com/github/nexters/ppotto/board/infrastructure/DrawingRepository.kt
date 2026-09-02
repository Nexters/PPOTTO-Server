package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.DrawingType
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.jooq.tables.records.DrawingsRecord
import com.github.nexters.ppotto.jooq.tables.references.DRAWINGS
import org.jooq.DSLContext
import org.jooq.InsertOnDuplicateSetMoreStep
import org.jooq.InsertOnDuplicateSetStep
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
    fun upsertAll(drawings: List<NewDrawing>): List<Drawing> =
        drawings
            .takeIf { it.isNotEmpty() }
            ?.let { targets ->
                insertAll(targets)
                    .overwriteWithExcluded()
                    .where(DRAWINGS.BOARD_ID.eq(excluded(DRAWINGS.BOARD_ID)))
                    .returning()
                    .fetch()
                    .map { it.toDomain() }
                    .associateBy { it.id }
                    .let { upserted -> targets.map { drawing -> upserted.getValue(drawing.id) } }
            } ?: emptyList()

    private fun insertAll(drawings: List<NewDrawing>): InsertOnDuplicateSetStep<DrawingsRecord> {
        var insert = dslContext.insertIntoDrawings()
        drawings.forEach { drawing ->
            insert =
                when (drawing) {
                    is NewDrawing.Stroke ->
                        insert.values(
                            drawing.id,
                            drawing.boardId,
                            drawing.stickerId,
                            drawing.scope.name,
                            drawing.type.name,
                            drawing.zIndex,
                            drawing.color,
                            JSONB.jsonb(objectMapper.writeValueAsString(drawing.stroke)),
                            drawing.strokeWidth,
                            null,
                            null,
                            null,
                            null,
                            null,
                            NO_ROTATION,
                        )

                    is NewDrawing.Text ->
                        insert.values(
                            drawing.id,
                            drawing.boardId,
                            drawing.stickerId,
                            drawing.scope.name,
                            drawing.type.name,
                            drawing.zIndex,
                            drawing.color,
                            null,
                            null,
                            drawing.content,
                            drawing.fontSize,
                            drawing.posX,
                            drawing.posY,
                            drawing.maxWidth,
                            drawing.rotation,
                        )
                }
        }
        return insert.onConflict(DRAWINGS.ID).doUpdate()
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

    private fun DrawingsRecord.toDomain(): Drawing =
        when (DrawingType.valueOf(type!!)) {
            DrawingType.STROKE ->
                Drawing.Stroke(
                    id = id!!,
                    boardId = boardId,
                    stickerId = stickerId,
                    scope = DrawingScope.valueOf(scope),
                    color = color,
                    zIndex = zIndex!!,
                    createdAt = createdAt!!,
                    updatedAt = updatedAt!!,
                    stroke = objectMapper.readValue(stroke!!.data()),
                    strokeWidth = strokeWidth!!,
                )

            DrawingType.TEXT ->
                Drawing.Text(
                    id = id!!,
                    boardId = boardId,
                    stickerId = stickerId,
                    scope = DrawingScope.valueOf(scope),
                    color = color,
                    zIndex = zIndex!!,
                    createdAt = createdAt!!,
                    updatedAt = updatedAt!!,
                    content = content!!,
                    fontSize = fontSize!!,
                    posX = posX!!,
                    posY = posY!!,
                    maxWidth = maxWidth!!,
                    rotation = rotation!!,
                )
        }

    private companion object {
        const val NO_ROTATION = 0.0
    }
}

private fun DSLContext.insertIntoDrawings() =
    insertInto(
        DRAWINGS,
        DRAWINGS.ID,
        DRAWINGS.BOARD_ID,
        DRAWINGS.STICKER_ID,
        DRAWINGS.SCOPE,
        DRAWINGS.TYPE,
        DRAWINGS.Z_INDEX,
        DRAWINGS.COLOR,
        DRAWINGS.STROKE,
        DRAWINGS.STROKE_WIDTH,
        DRAWINGS.CONTENT,
        DRAWINGS.FONT_SIZE,
        DRAWINGS.POS_X,
        DRAWINGS.POS_Y,
        DRAWINGS.MAX_WIDTH,
        DRAWINGS.ROTATION,
    )

private fun InsertOnDuplicateSetStep<DrawingsRecord>.overwriteWithExcluded(): InsertOnDuplicateSetMoreStep<DrawingsRecord> =
    set(DRAWINGS.STICKER_ID, excluded(DRAWINGS.STICKER_ID))
        .set(DRAWINGS.SCOPE, excluded(DRAWINGS.SCOPE))
        .set(DRAWINGS.TYPE, excluded(DRAWINGS.TYPE))
        .set(DRAWINGS.Z_INDEX, excluded(DRAWINGS.Z_INDEX))
        .set(DRAWINGS.COLOR, excluded(DRAWINGS.COLOR))
        .set(DRAWINGS.STROKE, excluded(DRAWINGS.STROKE))
        .set(DRAWINGS.STROKE_WIDTH, excluded(DRAWINGS.STROKE_WIDTH))
        .set(DRAWINGS.CONTENT, excluded(DRAWINGS.CONTENT))
        .set(DRAWINGS.FONT_SIZE, excluded(DRAWINGS.FONT_SIZE))
        .set(DRAWINGS.POS_X, excluded(DRAWINGS.POS_X))
        .set(DRAWINGS.POS_Y, excluded(DRAWINGS.POS_Y))
        .set(DRAWINGS.MAX_WIDTH, excluded(DRAWINGS.MAX_WIDTH))
        .set(DRAWINGS.ROTATION, excluded(DRAWINGS.ROTATION))
        .setNull(DRAWINGS.DELETED_AT)
