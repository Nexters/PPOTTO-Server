package com.github.nexters.ppotto.analysis.application.port

interface StickerGenerator {
    fun generate(
        sourceUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray
}
