package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.support.ResettableFake
import java.util.concurrent.ConcurrentHashMap

class FakeStickerStorage :
    StickerStorage,
    ResettableFake {
    val uploaded: MutableMap<String, ByteArray> = ConcurrentHashMap()

    var uploadFailure: Throwable? = null

    override fun upload(
        objectKey: String,
        bytes: ByteArray,
    ) {
        uploadFailure?.let { throw it }
        uploaded[objectKey] = bytes
    }

    override fun reset() {
        uploaded.clear()
        uploadFailure = null
    }
}
