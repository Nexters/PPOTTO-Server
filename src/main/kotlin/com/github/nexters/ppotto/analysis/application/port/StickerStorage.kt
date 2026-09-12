package com.github.nexters.ppotto.analysis.application.port

interface StickerStorage {
    fun upload(
        objectKey: String,
        bytes: ByteArray,
    )
}
