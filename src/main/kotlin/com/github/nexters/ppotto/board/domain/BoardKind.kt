package com.github.nexters.ppotto.board.domain

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "보드 종류. 참여자가 한 명이라도 있으면 SHARED", enumAsRef = true)
enum class BoardKind {
    PERSONAL,
    SHARED,
    ;

    companion object {
        fun of(memberCount: Int): BoardKind = if (memberCount > 0) SHARED else PERSONAL
    }
}
