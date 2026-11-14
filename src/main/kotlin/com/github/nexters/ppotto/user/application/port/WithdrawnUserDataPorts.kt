package com.github.nexters.ppotto.user.application.port

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId

interface WithdrawnUserBoardDeletionPort {
    fun findAllBoardIds(userId: UserId): List<BoardId>

    fun deleteAllByUserId(userId: UserId)
}

fun interface WithdrawnUserStickerDeletionPort {
    fun deleteAllByUserAndBoardIds(
        userId: UserId,
        boardIds: Collection<BoardId>,
    )
}

fun interface WithdrawnUserAnalysisDeletionPort {
    fun deleteAllByUserId(userId: UserId)
}

fun interface WithdrawnUserTermAgreementDeletionPort {
    fun deleteAllByUserId(userId: UserId)
}
