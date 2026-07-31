package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.global.storage.GcsReadUrlIssuer
import com.github.nexters.ppotto.global.storage.ObjectStorageCleaner
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import org.springframework.stereotype.Component

@Component
class GcsStickerImageStorage(
    private val gcsReadUrlIssuer: GcsReadUrlIssuer,
    private val objectStorageCleaner: ObjectStorageCleaner,
) : StickerImageStoragePort {
    override fun issueReadUrls(imageKeys: Collection<String>): Map<String, String> = gcsReadUrlIssuer.issue(imageKeys)

    override fun deleteAll(imageKeys: Collection<String>): Int = objectStorageCleaner.deleteAll(imageKeys)
}
