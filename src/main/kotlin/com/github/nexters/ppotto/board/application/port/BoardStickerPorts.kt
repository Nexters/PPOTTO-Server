package com.github.nexters.ppotto.board.application.port

import com.github.nexters.ppotto.board.domain.BoardStickerType
import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId

fun interface BoardStickerQueryPort {
    fun getByBoardId(boardId: BoardId): List<BoardStickerItem>
}

interface BoardStickerCommandPort {
    fun ownsAll(
        boardId: BoardId,
        stickerIds: Set<StickerId>,
    ): Boolean

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
    val type: BoardStickerType,
    val imageUrl: String?,
    val textContent: String?,
    val posX: Double?,
    val posY: Double?,
    val scale: Double,
    val rotation: Double,
    val zIndex: Int?,
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
) {
    init {
        val invalid =
            !posX.isFinite() ||
                !posY.isFinite() ||
                !scale.isFinite() ||
                scale <= 0 ||
                !rotation.isFinite() ||
                !badgeOffsetX.isFinite() ||
                !badgeOffsetY.isFinite() ||
                !badgeRotation.isFinite()
        if (invalid) throw InvalidInputException(CommonErrorCode.INVALID_INPUT)
    }

    companion object {
        const val MAX_TITLE_LENGTH = 15
    }
}
