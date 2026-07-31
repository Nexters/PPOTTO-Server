package com.github.nexters.ppotto.sticker.support

import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.application.port.StickerImageQueryPort
import java.util.UUID

class FakeRecapPhotoQueryPort : RecapPhotoQueryPort {
    val photos = mutableMapOf<UUID, RecapPhotoMetadata>()

    override fun getByIds(photoIds: Collection<UUID>): List<RecapPhotoMetadata> = photoIds.mapNotNull(photos::get)
}

class FakeStickerImageQueryPort : StickerImageQueryPort {
    override fun issueReadUrls(imageKeys: Collection<String>): Map<String, String> = imageKeys.associateWith { "https://example.com/$it" }
}
