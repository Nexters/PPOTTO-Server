package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.support.ResettableFake
import java.util.concurrent.ConcurrentHashMap

class FakeStickerStorage :
    StickerStorage,
    ResettableFake {
    val uploaded: MutableMap<String, ByteArray> = ConcurrentHashMap()

    override fun upload(
        objectKey: String,
        bytes: ByteArray,
    ) {
        uploaded[objectKey] = bytes
    }

    override fun reset() {
        uploaded.clear()
    }
}
