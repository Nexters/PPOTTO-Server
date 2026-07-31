package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.global.storage.GcsReadUrlIssuer
import com.github.nexters.ppotto.sticker.application.port.StickerImageQueryPort
import org.springframework.stereotype.Component

@Component
class GcsStickerImageStorage(
    private val gcsReadUrlIssuer: GcsReadUrlIssuer,
) : StickerImageQueryPort {
    override fun issueReadUrls(imageKeys: Collection<String>): Map<String, String> = gcsReadUrlIssuer.issue(imageKeys)
}
