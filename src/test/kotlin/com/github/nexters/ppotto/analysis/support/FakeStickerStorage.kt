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

    override fun issueReadUrls(objectKeys: List<String>): List<String> = objectKeys.map { "https://fake-sticker-url/$it" }
}
