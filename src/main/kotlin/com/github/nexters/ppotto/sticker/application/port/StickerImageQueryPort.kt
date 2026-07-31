package com.github.nexters.ppotto.sticker.application.port

interface StickerImageQueryPort {
    fun issueReadUrls(imageKeys: Collection<String>): Map<String, String>
}
