package com.github.nexters.ppotto.board.domain

enum class BoardShareScope(
    val includesRecap: Boolean,
    val includesPhotos: Boolean,
) {
    BOARD(includesRecap = false, includesPhotos = false),
    RECAP(includesRecap = true, includesPhotos = false),
    PHOTOS(includesRecap = true, includesPhotos = true),
    ;

    companion object {
        val DEFAULT = RECAP
    }
}
