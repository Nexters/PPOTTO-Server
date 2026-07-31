package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.StickerGenerator

class FakeStickerGenerator : StickerGenerator {
    override fun generate(
        sourceGcsUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray = byteArrayOf(1, 2, 3)
}
