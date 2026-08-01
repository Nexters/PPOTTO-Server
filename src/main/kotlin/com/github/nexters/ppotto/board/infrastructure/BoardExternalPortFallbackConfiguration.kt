package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.application.port.BoardStickerCommandPort
import com.github.nexters.ppotto.board.application.port.BoardStickerLayoutCommand
import com.github.nexters.ppotto.board.application.port.BoardStickerQueryPort
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.StickerId
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class BoardExternalPortFallbackConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun boardAnalysisActivityPort(): BoardAnalysisActivityPort =
        BoardAnalysisActivityPort { _, _ -> error("AnalysisQueryService 연동이 필요합니다.") }

    @Bean
    @ConditionalOnMissingBean
    fun boardStickerQueryPort(): BoardStickerQueryPort = BoardStickerQueryPort { error("StickerQueryService 연동이 필요합니다.") }

    @Bean
    @ConditionalOnMissingBean
    fun boardStickerCommandPort(): BoardStickerCommandPort =
        object : BoardStickerCommandPort {
            override fun validateOwnedByBoard(
                boardId: BoardId,
                stickerIds: Set<StickerId>,
            ) = error("StickerCommandService 연동이 필요합니다.")

            override fun updateLayouts(
                boardId: BoardId,
                layouts: List<BoardStickerLayoutCommand>,
            ) = error("StickerCommandService 연동이 필요합니다.")

            override fun deleteAllByBoardId(boardId: BoardId) = error("StickerCommandService 연동이 필요합니다.")
        }
}
