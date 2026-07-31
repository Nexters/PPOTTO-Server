package com.github.nexters.ppotto.user.support

import com.github.nexters.ppotto.user.application.port.WithdrawnUserDataDeletionPort
import java.util.UUID

class FakeWithdrawnUserDataDeletionPort : WithdrawnUserDataDeletionPort {
    val deletedUserIds = mutableListOf<UUID>()
    val failingUserIds = mutableSetOf<UUID>()

    override fun deleteAllFor(userId: UUID) {
        if (userId in failingUserIds) {
            error("연관 데이터 삭제 실패")
        }
        deletedUserIds += userId
    }

    fun clear() {
        deletedUserIds.clear()
        failingUserIds.clear()
    }
}
