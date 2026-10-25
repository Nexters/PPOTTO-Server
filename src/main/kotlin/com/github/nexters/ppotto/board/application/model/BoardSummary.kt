package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.application.port.BoardAnalysisStatus
import com.github.nexters.ppotto.board.domain.BoardKind
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.global.identifier.BoardId
import java.time.Instant

data class BoardSummary(
    val id: BoardId,
    val name: String,
    val kind: BoardKind,
    val role: BoardRole,
    val stickerCount: Int,
    val participantCount: Int,
    val previewImageUrls: List<String>,
    val mainColor: String?,
    val lastActivityAt: Instant,
    val analysisStatus: BoardAnalysisStatus?,
) {
    companion object {
        const val MAX_PREVIEW_IMAGE_COUNT = 4
    }
}
