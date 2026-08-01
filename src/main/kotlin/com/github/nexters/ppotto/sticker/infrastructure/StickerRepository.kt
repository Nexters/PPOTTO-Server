package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.jooq.tables.records.StickersRecord
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerCreation
import com.github.nexters.ppotto.sticker.domain.StickerType
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

data class StickerDeletionTarget(
    val id: StickerId,
    val imageKey: String?,
)

@Repository
class StickerRepository(
    private val dslContext: DSLContext,
) {
    fun save(
        analysisId: AnalysisId,
        boardId: BoardId,
        creation: StickerCreation,
    ): Sticker =
        dslContext
            .insertInto(
                STICKERS,
                STICKERS.ANALYSIS_ID,
                STICKERS.BOARD_ID,
                STICKERS.TYPE,
                STICKERS.TITLE,
                STICKERS.SUMMARY,
                STICKERS.SOURCE_PHOTO_ID,
                STICKERS.IMAGE_KEY,
                STICKERS.TEXT_CONTENT,
                STICKERS.POS_X,
                STICKERS.POS_Y,
                STICKERS.SCALE,
                STICKERS.ROTATION,
                STICKERS.Z_INDEX,
                STICKERS.BADGE_OFFSET_X,
                STICKERS.BADGE_OFFSET_Y,
                STICKERS.BADGE_ROTATION,
            ).values(
                analysisId.value,
                boardId.value,
                creation.type.name,
                creation.title,
                creation.summary,
                creation.sourcePhotoId?.value,
                creation.imageKey,
                creation.textContent,
                creation.layout.posX,
                creation.layout.posY,
                creation.layout.scale,
                creation.layout.rotation,
                creation.layout.zIndex,
                creation.layout.badgeOffsetX,
                creation.layout.badgeOffsetY,
                creation.layout.badgeRotation,
            ).returning()
            .fetchSingle()
            .toDomain()

    fun findById(id: StickerId): Sticker? =
        dslContext
            .selectFrom(STICKERS)
            .where(STICKERS.ID.eq(id.value))
            .and(STICKERS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun findAllByBoardId(boardId: BoardId): List<Sticker> =
        dslContext
            .selectFrom(STICKERS)
            .where(STICKERS.BOARD_ID.eq(boardId.value))
            .and(STICKERS.DELETED_AT.isNull)
            .orderBy(STICKERS.Z_INDEX.asc(), STICKERS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun findAllByAnalysisId(analysisId: AnalysisId): List<Sticker> =
        dslContext
            .selectFrom(STICKERS)
            .where(STICKERS.ANALYSIS_ID.eq(analysisId.value))
            .orderBy(STICKERS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun lockAnalysisResult(analysisId: AnalysisId) {
        dslContext.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
            analysisId.value,
        )
    }

    fun validateOwnedByBoard(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    ): Boolean =
        stickerIds.toSet().let { uniqueIds ->
            uniqueIds.isEmpty() ||
                dslContext
                    .selectCount()
                    .from(STICKERS)
                    .where(STICKERS.BOARD_ID.eq(boardId.value))
                    .and(STICKERS.ID.`in`(uniqueIds.map(StickerId::value)))
                    .and(STICKERS.DELETED_AT.isNull)
                    .fetchSingle(0, Int::class.java) == uniqueIds.size
        }

    fun findDeletionTargetsByBoardIds(boardIds: Collection<BoardId>): List<StickerDeletionTarget> =
        boardIds
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { uniqueIds ->
                dslContext
                    .select(STICKERS.ID, STICKERS.IMAGE_KEY)
                    .from(STICKERS)
                    .where(STICKERS.BOARD_ID.`in`(uniqueIds.map(BoardId::value)))
                    .fetch()
                    .map { record -> StickerDeletionTarget(StickerId(record.value1()!!), record.value2()) }
            } ?: emptyList()

    private fun StickersRecord.toDomain() =
        Sticker(
            id = StickerId(id!!),
            analysisId = AnalysisId(analysisId),
            boardId = BoardId(boardId),
            type = StickerType.valueOf(type),
            title = title,
            summary = summary,
            viewedAt = viewedAt,
            sourcePhotoId = sourcePhotoId?.let(::PhotoId),
            imageKey = imageKey,
            textContent = textContent,
            posX = posX,
            posY = posY,
            scale = scale!!,
            rotation = rotation!!,
            zIndex = zIndex!!,
            badgeOffsetX = badgeOffsetX!!,
            badgeOffsetY = badgeOffsetY!!,
            badgeRotation = badgeRotation!!,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
            deletedAt = deletedAt,
        )
}
