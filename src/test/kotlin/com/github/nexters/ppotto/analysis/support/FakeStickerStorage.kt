package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.application.port.StickerStorage
import com.github.nexters.ppotto.analysis.infrastructure.storage.StickerObjectKeys
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.support.ResettableFake
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeStickerStorage :
    StickerStorage,
    ResettableFake {
    val uploaded: MutableMap<String, ByteArray> = ConcurrentHashMap()
    val deletedAnalysisIds = CopyOnWriteArrayList<AnalysisId>()

    var uploadFailure: Throwable? = null
    var deleteAllFailure: Throwable? = null
    var onUpload: ((String) -> Unit)? = null

    override fun upload(
        objectKey: String,
        bytes: ByteArray,
    ) {
        uploadFailure?.let { throw it }
        onUpload?.invoke(objectKey)
        uploaded[objectKey] = bytes
    }

    override fun deleteAll(analysisId: AnalysisId): Int {
        deleteAllFailure?.let { throw it }
        deletedAnalysisIds += analysisId
        val deletedKeys = uploaded.keys.filter { it.startsWith(StickerObjectKeys.prefixFor(analysisId)) }
        deletedKeys.forEach { uploaded -= it }
        return deletedKeys.size
    }

    override fun reset() {
        uploaded.clear()
        deletedAnalysisIds.clear()
        uploadFailure = null
        deleteAllFailure = null
        onUpload = null
    }
}
