package com.github.nexters.ppotto.user.support

import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.WithdrawnUserDataDeletionPort

class FakeWithdrawnUserDataDeletionPort : WithdrawnUserDataDeletionPort {
    val deletedUserIds = mutableListOf<UserId>()
    val failingUserIds = mutableSetOf<UserId>()

    override fun deleteAllFor(userId: UserId) {
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
