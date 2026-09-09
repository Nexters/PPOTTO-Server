package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.support.ResettableFake
import java.util.concurrent.CopyOnWriteArrayList

class FakeStickerGenerator :
    StickerGenerator,
    ResettableFake {
    var onGenerate: ((String) -> Unit)? = null

    val requestedTargetSubjects: MutableList<String> = CopyOnWriteArrayList()

    override fun generate(
        sourceUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray {
        requestedTargetSubjects += targetSubject
        onGenerate?.invoke(targetSubject)
        return byteArrayOf(1, 2, 3)
    }

    override fun reset() {
        onGenerate = null
        requestedTargetSubjects.clear()
    }
}
