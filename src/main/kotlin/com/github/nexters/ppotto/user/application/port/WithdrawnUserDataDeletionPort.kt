package com.github.nexters.ppotto.user.application.port

import java.util.UUID

fun interface WithdrawnUserDataDeletionPort {
    fun deleteAllFor(userId: UUID)
}
