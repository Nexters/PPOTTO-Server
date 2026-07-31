package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.sticker.application.port.StickerImageQueryPort
import org.springframework.stereotype.Component

@Component
class StickerImageQueryAdapter(
    private val stickerStorage: StickerStorage,
) : StickerImageQueryPort {
    override fun issueReadUrls(imageKeys: Collection<String>): Map<String, String> =
        imageKeys
            .takeIf { it.isNotEmpty() }
            ?.let { keys ->
                keys
                    .toList()
                    .let { stickerStorage.issueReadUrls(it) }
                    .zip(keys)
                    .associate { (url, key) -> key to url }
            }
            .orEmpty()
}
