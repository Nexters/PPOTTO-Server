package com.github.nexters.ppotto.analysis.application.port

interface StickerBackgroundRemover {
    fun removeBackground(
        imageBytes: ByteArray,
        mimeType: String,
    ): ByteArray
}
