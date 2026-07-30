package com.github.nexters.ppotto.user.support

import com.github.nexters.ppotto.global.error.UnauthorizedException
import com.github.nexters.ppotto.user.application.port.CurrentUserProvider
import java.util.UUID

class FakeCurrentUserProvider : CurrentUserProvider {
    var userId: UUID? = null

    override fun currentUserId(): UUID = userId ?: throw UnauthorizedException()
}
