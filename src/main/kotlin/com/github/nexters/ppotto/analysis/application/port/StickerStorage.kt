package com.github.nexters.ppotto.analysis.application.port

import com.github.nexters.ppotto.global.identifier.AnalysisId

interface StickerStorage {
    fun upload(
        objectKey: String,
        bytes: ByteArray,
    )

    fun deleteAll(analysisId: AnalysisId): Int
}
