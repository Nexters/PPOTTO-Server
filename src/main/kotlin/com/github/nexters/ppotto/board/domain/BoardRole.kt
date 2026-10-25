package com.github.nexters.ppotto.board.domain

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "요청한 사용자의 보드 내 역할. 방장은 boards.user_id, 참여자는 board_members", enumAsRef = true)
enum class BoardRole {
    OWNER,
    MEMBER,
}
