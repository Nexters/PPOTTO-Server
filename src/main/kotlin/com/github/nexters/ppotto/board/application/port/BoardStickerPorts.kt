package com.github.nexters.ppotto.board.application.port

import java.util.UUID

fun interface BoardStickerQueryPort {
    fun getByBoardId(boardId: UUID): List<BoardStickerItem>
}

interface BoardStickerCommandPort {
    fun validateOwnedByBoard(
        boardId: UUID,
        stickerIds: Set<UUID>,
    )

    fun updateLayouts(
        boardId: UUID,
        layouts: List<BoardStickerLayoutCommand>,
    )

    fun deleteAllByBoardId(boardId: UUID)
}

data class BoardStickerItem(
    val id: UUID,
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
    val id: UUID,
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
