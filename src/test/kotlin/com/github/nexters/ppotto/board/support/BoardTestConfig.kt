package com.github.nexters.ppotto.board.support

import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class BoardTestConfig {
    @Bean
    @Primary
    fun boardAnalysisActivityPort(): FakeBoardAnalysisActivityPort = FakeBoardAnalysisActivityPort()

    @Bean
    @Primary
    fun boardStickerPort(): FakeBoardStickerPort = FakeBoardStickerPort()
}

class FakeBoardAnalysisActivityPort : BoardAnalysisActivityPort {
    val activeBoardIds = mutableSetOf<BoardId>()

    override fun hasActiveAnalysis(
        boardId: BoardId,
        userId: UserId,
    ): Boolean = boardId in activeBoardIds

    fun reset() {
        activeBoardIds.clear()
    }
}

class FakeBoardStickerPort :
    BoardStickerQueryPort,
    BoardStickerCommandPort {
    val stickersByBoardId = mutableMapOf<BoardId, List<BoardStickerItem>>()
    val validatedStickerIds = mutableListOf<Set<StickerId>>()
    val updatedLayouts = mutableListOf<List<BoardStickerLayoutCommand>>()
    val deletedBoardIds = mutableListOf<BoardId>()

    override fun getByBoardId(boardId: BoardId): List<BoardStickerItem> = stickersByBoardId[boardId].orEmpty()

    override fun validateOwnedByBoard(
        boardId: BoardId,
        stickerIds: Set<StickerId>,
    ) {
        val ownedIds =
            stickersByBoardId[boardId]
                .orEmpty()
                .map { it.id }
                .toSet()
        if (!ownedIds.containsAll(stickerIds)) {
            throw InvalidInputException(BoardErrorCode.INVALID_LAYOUT)
        }
        validatedStickerIds += stickerIds
    }

    override fun updateLayouts(
        boardId: BoardId,
        layouts: List<BoardStickerLayoutCommand>,
    ) {
        validateOwnedByBoard(boardId, layouts.map { it.id }.toSet())
        updatedLayouts += layouts
    }

    override fun deleteAllByBoardId(boardId: BoardId) {
        stickersByBoardId.remove(boardId)
        deletedBoardIds += boardId
    }

    fun reset() {
        stickersByBoardId.clear()
        validatedStickerIds.clear()
        updatedLayouts.clear()
        deletedBoardIds.clear()
    }
}
