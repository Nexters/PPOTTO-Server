package com.github.nexters.ppotto.analysis.domain

interface StickerStorage {
    fun upload(
        objectKey: String,
        bytes: ByteArray,
    ): String

    fun issueReadUrls(objectKeys: List<String>): List<String>
}
