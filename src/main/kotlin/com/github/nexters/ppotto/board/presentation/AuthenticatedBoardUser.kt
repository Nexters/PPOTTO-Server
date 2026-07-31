package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.global.error.UnauthorizedException
import java.util.UUID

internal fun UUID?.requireAuthenticatedUserId(): UUID = this ?: throw UnauthorizedException()
