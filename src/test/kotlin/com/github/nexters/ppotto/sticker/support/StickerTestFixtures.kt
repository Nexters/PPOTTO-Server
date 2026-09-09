package com.github.nexters.ppotto.sticker.support

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import org.jooq.DSLContext

fun imageStickerCreation(
    sourcePhotoId: PhotoId,
    imageKey: String = "stickers/original.png",
    title: String = "원래 제목",
    summary: String = "한 줄 요약",
    mainColor: String = "#FF6B6B",
): StickerCreation =
    StickerCreation(
        type = StickerType.IMAGE,
        title = title,
        summary = summary,
        sourcePhotoId = sourcePhotoId,
        imageKey = imageKey,
        textContent = null,
        mainColor = mainColor,
    )

fun textStickerCreation(
    title: String = "원래 제목",
    summary: String = "한 줄 요약",
    textContent: String = "텍스트",
    mainColor: String = "#FF6B6B",
): StickerCreation =
    StickerCreation(
        type = StickerType.TEXT,
        title = title,
        summary = summary,
        sourcePhotoId = null,
        imageKey = null,
        textContent = textContent,
        mainColor = mainColor,
    )

fun imageStickerResult(
    photoId: PhotoId,
    title: String = "이미지 스티커",
    summary: String = "웃기고 귀여우면 일단 주워요",
    imageKey: String = "stickers/image.png",
): AnalysisStickerResult =
    AnalysisStickerResult(
        type = StickerType.IMAGE,
        title = title,
        summary = summary,
        sourcePhotoId = photoId,
        imageKey = imageKey,
        textContent = null,
        mainColor = "#FF6B6B",
        photoIds = listOf(photoId),
        comments =
            listOf(
                RecapCommentCreation("말풍선", 3.0, 4.0),
                RecapCommentCreation("키워드 칩", null, null),
            ),
    )

fun textStickerResult(
    title: String = "텍스트 스티커",
    summary: String = "한 줄 요약",
): AnalysisStickerResult =
    AnalysisStickerResult(
        type = StickerType.TEXT,
        title = title,
        summary = summary,
        sourcePhotoId = null,
        imageKey = null,
        textContent = "텍스트",
        mainColor = "#FF6B6B",
        photoIds = emptyList(),
        comments = emptyList(),
    )

fun stickerLayout(
    title: String? = null,
    posX: Double = 10.0,
    posY: Double = 20.0,
    zIndex: Int = 2,
): StickerLayout =
    StickerLayout(
        title = title,
        posX = posX,
        posY = posY,
        scale = 0.8,
        rotation = 5.0,
        zIndex = zIndex,
        badgeOffsetX = 3.0,
        badgeOffsetY = 4.0,
        badgeRotation = 6.0,
    )

/**
 * 읽은 뒤 다른 트랜잭션이 소프트 삭제를 커밋한 상황을 재현한다. 실제로는 락 없이 벌어지는 경쟁이라 테스트에서 재현할 수 없어,
 * 조회 결과만 그때의 aggregate 로 고정하고 쓰기는 실제 테이블로 흘려보낸다. 이 구멍이 STICKER-001/007/008 의 존재 이유다.
 */
class StaleStickerRepository(
    dslContext: DSLContext,
    private val staleStickers: List<Sticker>,
) : StickerRepository(dslContext) {
    override fun findById(id: StickerId): Sticker? = staleStickers.firstOrNull { it.id == id }

    override fun findAllByBoardId(boardId: BoardId): List<Sticker> = staleStickers.filter { it.boardId == boardId }
}
