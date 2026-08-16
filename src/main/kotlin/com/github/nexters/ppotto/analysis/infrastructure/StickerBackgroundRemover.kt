package com.github.nexters.ppotto.analysis.infrastructure

interface StickerBackgroundRemover {
    fun removeBackground(
        imageBytes: ByteArray,
        mimeType: String,
    ): ByteArray
}
