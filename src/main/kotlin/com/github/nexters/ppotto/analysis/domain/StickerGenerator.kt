package com.github.nexters.ppotto.analysis.domain

interface StickerGenerator {
    fun generate(
        sourceGcsUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray
}
