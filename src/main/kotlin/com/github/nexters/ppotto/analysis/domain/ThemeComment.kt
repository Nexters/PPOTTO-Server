package com.github.nexters.ppotto.analysis.domain

data class ThemeComment(
    val content: String,
    val posX: Double?,
    val posY: Double?,
) {
    companion object {
        const val MAX_BUBBLE_LENGTH = 20
        const val MAX_CHIP_LENGTH = 20
    }
}
