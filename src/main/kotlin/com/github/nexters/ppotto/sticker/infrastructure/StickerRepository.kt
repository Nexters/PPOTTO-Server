package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
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
                STICKERS.MAIN_COLOR,
            ).values(
                analysisId,
                boardId,
                creation.type.name,
                creation.title,
                creation.summary,
                creation.sourcePhotoId,
                creation.imageKey,
                creation.textContent,
                creation.mainColor,
            ).returning()
            .fetchSingle()
            .toDomain()

    fun findById(id: StickerId): Sticker? =
        dslContext
            .selectFrom(STICKERS)
            .where(STICKERS.ID.eq(id))
            .and(STICKERS.DELETED_AT.isNull)
            .fetchOne()
            ?.toDomain()

    fun findAllByBoardId(boardId: BoardId): List<Sticker> =
        dslContext
            .selectFrom(STICKERS)
            .where(STICKERS.BOARD_ID.eq(boardId))
            .and(STICKERS.DELETED_AT.isNull)
            .orderBy(STICKERS.Z_INDEX.asc(), STICKERS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun findAllByAnalysisId(analysisId: AnalysisId): List<Sticker> =
        dslContext
            .selectFrom(STICKERS)
            .where(STICKERS.ANALYSIS_ID.eq(analysisId))
            .orderBy(STICKERS.ID.asc())
            .fetch()
            .map { it.toDomain() }

    fun validateOwnedByBoard(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    ): Boolean {
        val uniqueIds = stickerIds.toSet()
        if (uniqueIds.isEmpty()) {
            return true
        }
        return dslContext
            .selectCount()
            .from(STICKERS)
            .where(STICKERS.BOARD_ID.eq(boardId))
            .and(STICKERS.ID.`in`(uniqueIds))
            .and(STICKERS.DELETED_AT.isNull)
            .fetchSingle(0, Int::class.java) == uniqueIds.size
    }

    fun findDeletionTargetsByBoardIds(boardIds: Collection<BoardId>): List<StickerDeletionTarget> =
        dslContext
            .select(STICKERS.ID, STICKERS.IMAGE_KEY)
            .from(STICKERS)
            .where(STICKERS.BOARD_ID.`in`(boardIds.toSet()))
            .fetch()
            .map { record -> StickerDeletionTarget(record.value1()!!, record.value2()) }

    private fun StickersRecord.toDomain() =
        Sticker(
            id = id!!,
            analysisId = analysisId,
            boardId = boardId,
            type = StickerType.valueOf(type),
            title = title,
            summary = summary,
            viewedAt = viewedAt,
            sourcePhotoId = sourcePhotoId,
            imageKey = imageKey,
            textContent = textContent,
            mainColor = mainColor!!,
            posX = posX,
            posY = posY,
            scale = scale!!,
            rotation = rotation!!,
            zIndex = zIndex,
            badgeOffsetX = badgeOffsetX!!,
            badgeOffsetY = badgeOffsetY!!,
            badgeRotation = badgeRotation!!,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
            deletedAt = deletedAt,
        )
}
