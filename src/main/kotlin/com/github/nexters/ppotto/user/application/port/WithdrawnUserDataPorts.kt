package com.github.nexters.ppotto.user.application.port

import java.util.UUID

interface WithdrawnUserBoardDeletionPort {
    fun findAllBoardIds(userId: UUID): List<UUID>

    fun deleteAllByUserId(userId: UUID)
}

fun interface WithdrawnUserStickerDeletionPort {
    fun deleteAllByBoardIds(boardIds: Collection<UUID>)
}

fun interface WithdrawnUserAnalysisDeletionPort {
    fun deleteAllByUserId(userId: UUID)
}

fun interface WithdrawnUserTermAgreementDeletionPort {
    fun deleteAllByUserId(userId: UUID)
}
