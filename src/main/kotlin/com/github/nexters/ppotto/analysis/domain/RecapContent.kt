package com.github.nexters.ppotto.analysis.domain

data class RecapContent(
    val badge: String,
    val text: String,
) {
    companion object {
        const val MAX_BADGE_LENGTH = 15
        const val MAX_TEXT_LENGTH = 100
    }
}
