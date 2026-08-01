package com.github.nexters.ppotto.board.application.port

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId

fun interface BoardStickerQueryPort {
    fun getByBoardId(boardId: BoardId): List<BoardStickerItem>
}

interface BoardStickerCommandPort {
    fun validateOwnedByBoard(
        boardId: BoardId,
        stickerIds: Set<StickerId>,
    )

    fun updateLayouts(
        boardId: BoardId,
        layouts: List<BoardStickerLayoutCommand>,
    )

    fun deleteAllByBoardId(boardId: BoardId)
}

data class BoardStickerItem(
    val id: StickerId,
    val title: String,
    val isNew: Boolean,
    val type: String,
    val imageUrl: String?,
    val textContent: String?,
    val posX: Double,
    val posY: Double,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int,
    val badgeOffsetX: Double,
    val badgeOffsetY: Double,
    val badgeRotation: Double,
)

data class BoardStickerLayoutCommand(
    val id: StickerId,
    val title: String?,
    val posX: Double,
    val posY: Double,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int,
    val badgeOffsetX: Double,
    val badgeOffsetY: Double,
    val badgeRotation: Double,
)
