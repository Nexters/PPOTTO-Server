package com.github.nexters.ppotto.board.support

import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerItem
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.UUID

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
    val activeBoardIds = mutableSetOf<UUID>()

    override fun hasActiveAnalysis(
        boardId: UUID,
        userId: UUID,
    ): Boolean = boardId in activeBoardIds

    fun reset() {
        activeBoardIds.clear()
    }
}

class FakeBoardStickerPort :
    BoardStickerQueryPort,
    BoardStickerCommandPort {
    val stickersByBoardId = mutableMapOf<UUID, List<BoardStickerItem>>()
    val validatedStickerIds = mutableListOf<Set<UUID>>()
    val updatedLayouts = mutableListOf<List<BoardStickerLayoutCommand>>()
    val deletedBoardIds = mutableListOf<UUID>()

    override fun getByBoardId(boardId: UUID): List<BoardStickerItem> = stickersByBoardId[boardId].orEmpty()

    override fun validateOwnedByBoard(
        boardId: UUID,
        stickerIds: Set<UUID>,
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
        boardId: UUID,
        layouts: List<BoardStickerLayoutCommand>,
    ) {
        validateOwnedByBoard(boardId, layouts.map { it.id }.toSet())
        updatedLayouts += layouts
    }

    override fun deleteAllByBoardId(boardId: UUID) {
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
