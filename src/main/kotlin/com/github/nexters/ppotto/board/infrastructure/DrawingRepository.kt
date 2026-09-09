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

        val upserted =
            upsertQuery(drawings)
                .where(DRAWINGS.BOARD_ID.eq(excluded(DRAWINGS.BOARD_ID)))
                .returning()
                .fetch()
                .map { it.toDomain() }
                .associateBy { it.id }
        return drawings.map { upserted.getValue(it.id) }
    }

    private fun upsertQuery(drawings: List<NewDrawing>): InsertOnDuplicateSetMoreStep<DrawingsRecord> {
        val records = drawings.map { it.toRecord() }
        return records
            .drop(1)
            .fold(dslContext.insertInto(DRAWINGS).set(records.first())) { insert, record ->
                insert.newRecord().set(record)
            }.onConflict(DRAWINGS.ID)
            .doUpdate()
            .set(DRAWINGS.STICKER_ID, excluded(DRAWINGS.STICKER_ID))
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
    }

    fun findByBoardId(boardId: BoardId): List<Drawing> =
        dslContext
            .selectFrom(DRAWINGS)
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.DELETED_AT.isNull)
            .orderBy(DRAWINGS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun findBoardIdsByIds(ids: Collection<DrawingId>): Map<DrawingId, BoardId> {
        if (ids.isEmpty()) return emptyMap()

        return dslContext
            .select(DRAWINGS.ID, DRAWINGS.BOARD_ID)
            .from(DRAWINGS)
            .where(DRAWINGS.ID.`in`(ids))
            .fetch()
            .associate { record -> record.value1()!! to record.value2()!! }
    }

    fun findActiveIds(
        boardId: BoardId,
        ids: Collection<DrawingId>,
    ): Set<DrawingId> {
        if (ids.isEmpty()) return emptySet()

        return dslContext
            .select(DRAWINGS.ID)
            .from(DRAWINGS)
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.ID.`in`(ids))
            .and(DRAWINGS.DELETED_AT.isNull)
            .mapNotNull { record -> record.value1() }
            .toSet()
    }

    fun softDeleteByIds(
        boardId: BoardId,
        ids: Collection<DrawingId>,
    ): Int {
        if (ids.isEmpty()) return 0

        return dslContext
            .update(DRAWINGS)
            .set(DRAWINGS.DELETED_AT, Instant.now())
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.ID.`in`(ids))
            .and(DRAWINGS.DELETED_AT.isNull)
            .execute()
    }

    fun softDeleteByStickerIds(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    ): Int {
        if (stickerIds.isEmpty()) return 0

        return dslContext
            .update(DRAWINGS)
            .set(DRAWINGS.DELETED_AT, Instant.now())
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.STICKER_ID.`in`(stickerIds))
            .and(DRAWINGS.DELETED_AT.isNull)
            .execute()
    }

    fun softDeleteAllByBoardId(boardId: BoardId): Int =
        dslContext
            .update(DRAWINGS)
            .set(DRAWINGS.DELETED_AT, Instant.now())
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.DELETED_AT.isNull)
            .execute()

    fun hardDeleteAllByBoardIds(boardIds: Collection<BoardId>): Int {
        val ids = boardIds.toSet()
        if (ids.isEmpty()) return 0

        return dslContext
            .deleteFrom(DRAWINGS)
            .where(DRAWINGS.BOARD_ID.`in`(ids))
            .execute()
    }

    private fun NewDrawing.toRecord(): DrawingsRecord =
        dslContext.newRecord(DRAWINGS).also { record ->
            record.id = id
            record.boardId = boardId
            record.stickerId = stickerId
            record.scope = scope.name
            record.type = type.name
            record.zIndex = zIndex
            record.color = color
            when (this) {
                is NewDrawing.Stroke -> {
                    record.stroke = JSONB.jsonb(objectMapper.writeValueAsString(stroke))
                    record.strokeWidth = strokeWidth
                    record.content = null
                    record.fontSize = null
                    record.posX = null
                    record.posY = null
                    record.maxWidth = null
                    record.rotation = NO_ROTATION
                }

                is NewDrawing.Text -> {
                    record.stroke = null
                    record.strokeWidth = null
                    record.content = content
                    record.fontSize = fontSize
                    record.posX = posX
                    record.posY = posY
                    record.maxWidth = maxWidth
                    record.rotation = rotation
                }
            }
        }

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
