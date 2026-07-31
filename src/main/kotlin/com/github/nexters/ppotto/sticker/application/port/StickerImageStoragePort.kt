package com.github.nexters.ppotto.sticker.application.port

interface StickerImageStoragePort {
    fun issueReadUrls(imageKeys: Collection<String>): Map<String, String>

    fun deleteAll(imageKeys: Collection<String>): Int
}
