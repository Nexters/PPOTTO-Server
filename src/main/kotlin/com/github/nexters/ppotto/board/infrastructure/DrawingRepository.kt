package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.application.model.DrawingLayoutDeleteCommand
import com.github.nexters.ppotto.board.application.model.DrawingLayoutUpdateCommand
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.DrawingType
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.global.error.InvalidInputException
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
@Suppress("TooManyFunctions")
class DrawingRepository(
    private val dslContext: DSLContext,
    private val objectMapper: ObjectMapper,
) {
    fun upsertAll(drawings: List<NewDrawing>): List<Drawing> {
        if (drawings.isEmpty()) return emptyList()

        return upsertQuery(drawings)
            .where(DRAWINGS.BOARD_ID.eq(excluded(DRAWINGS.BOARD_ID)))
            .and(DRAWINGS.DELETED_AT.isNull)
            .returning()
            .fetch()
            .map { it.toDomain() }
            .associateBy { it.id }
            .let { upserted ->
                if (upserted.size != drawings.size) throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
                drawings.map { upserted.getValue(it.id) }
            }
    }

    fun insertAll(drawings: List<NewDrawing>) {
        drawings.forEach { drawing ->
            val inserted =
                dslContext
                    .insertInto(DRAWINGS)
                    .set(drawing.toRecord())
                    .onConflict(DRAWINGS.ID)
                    .doNothing()
                    .execute()
            if (inserted != 1) throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }
    }

    fun updateVersioned(command: DrawingLayoutUpdateCommand): Boolean {
        val drawing = command.drawing
        val record = drawing.toRecord()
        val query =
            dslContext
                .update(DRAWINGS)
                .set(DRAWINGS.Z_INDEX, drawing.zIndex)
                .set(DRAWINGS.COLOR, drawing.color)
                .set(DRAWINGS.LAYOUT_VERSION, DRAWINGS.LAYOUT_VERSION.plus(1L))
                .set(DRAWINGS.STROKE, record.stroke)
                .set(DRAWINGS.STROKE_WIDTH, record.strokeWidth)
                .set(DRAWINGS.CONTENT, record.content)
                .set(DRAWINGS.FONT_SIZE, record.fontSize)
                .set(DRAWINGS.POS_X, record.posX)
                .set(DRAWINGS.POS_Y, record.posY)
                .set(DRAWINGS.MAX_WIDTH, record.maxWidth)
                .set(DRAWINGS.ROTATION, record.rotation)
                .where(DRAWINGS.ID.eq(drawing.id))
                .and(DRAWINGS.BOARD_ID.eq(drawing.boardId))
                .and(DRAWINGS.DELETED_AT.isNull)
                .and(DRAWINGS.LAYOUT_VERSION.eq(command.expectedLayoutVersion))
                .and(DRAWINGS.TYPE.eq(drawing.type.name))
                .and(DRAWINGS.SCOPE.eq(drawing.scope.name))
                .and(
                    if (drawing.stickerId == null) {
                        DRAWINGS.STICKER_ID.isNull
                    } else {
                        DRAWINGS.STICKER_ID.eq(drawing.stickerId)
                    },
                )
        return query.execute() == 1
    }

    fun matchesIdentity(drawing: NewDrawing): Boolean =
        dslContext
            .selectOne()
            .from(DRAWINGS)
            .where(DRAWINGS.ID.eq(drawing.id))
            .and(DRAWINGS.BOARD_ID.eq(drawing.boardId))
            .and(DRAWINGS.TYPE.eq(drawing.type.name))
            .and(DRAWINGS.SCOPE.eq(drawing.scope.name))
            .and(
                if (drawing.stickerId == null) {
                    DRAWINGS.STICKER_ID.isNull
                } else {
                    DRAWINGS.STICKER_ID.eq(drawing.stickerId)
                },
            ).fetchOne() != null

    fun softDeleteVersioned(
        boardId: BoardId,
        command: DrawingLayoutDeleteCommand,
    ): Boolean =
        dslContext
            .update(DRAWINGS)
            .set(DRAWINGS.DELETED_AT, Instant.now())
            .where(DRAWINGS.BOARD_ID.eq(boardId))
            .and(DRAWINGS.ID.eq(command.id))
            .and(DRAWINGS.DELETED_AT.isNull)
            .and(DRAWINGS.LAYOUT_VERSION.eq(command.expectedLayoutVersion))
            .execute() == 1

    fun findActiveLayoutVersions(ids: Collection<DrawingId>): Map<DrawingId, Long> =
        ids
            .takeIf { it.isNotEmpty() }
            ?.let { drawingIds ->
                dslContext
                    .select(DRAWINGS.ID, DRAWINGS.LAYOUT_VERSION)
                    .from(DRAWINGS)
                    .where(DRAWINGS.ID.`in`(drawingIds))
                    .and(DRAWINGS.DELETED_AT.isNull)
                    .fetch()
                    .associate { it.value1()!! to it.value2()!! }
            } ?: emptyMap()

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
            .set(DRAWINGS.LAYOUT_VERSION, DRAWINGS.LAYOUT_VERSION.plus(1L))
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
                    layoutVersion = layoutVersion!!,
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
                    layoutVersion = layoutVersion!!,
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
