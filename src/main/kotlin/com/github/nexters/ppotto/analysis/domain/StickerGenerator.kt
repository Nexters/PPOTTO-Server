package com.github.nexters.ppotto.analysis.domain

interface StickerGenerator {
    fun generate(
        sourceUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray
}
