package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.StickerStorage

class FakeStickerStorage : StickerStorage {
    val uploaded = mutableMapOf<String, ByteArray>()

    override fun upload(
        objectKey: String,
        bytes: ByteArray,
    ): String {
        uploaded[objectKey] = bytes
        return objectKey
    }
}
