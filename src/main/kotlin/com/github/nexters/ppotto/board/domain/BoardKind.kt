package com.github.nexters.ppotto.board.domain

enum class BoardKind {
    PERSONAL,
    SHARED,
    ;

    companion object {
        fun of(memberCount: Int): BoardKind = if (memberCount > 0) SHARED else PERSONAL
    }
}
