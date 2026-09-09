package com.github.nexters.ppotto.analysis.domain

interface StickerBackgroundRemover {
    fun removeBackground(
        imageBytes: ByteArray,
        mimeType: String,
    ): ByteArray
}
