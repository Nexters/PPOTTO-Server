package com.github.nexters.ppotto.analysis.domain

interface StickerStorage {
    fun upload(
        objectKey: String,
        bytes: ByteArray,
    ): String
}
