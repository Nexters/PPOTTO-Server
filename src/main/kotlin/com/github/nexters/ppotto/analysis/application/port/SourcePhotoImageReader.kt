package com.github.nexters.ppotto.analysis.application.port

interface SourcePhotoImageReader {
    fun read(sourceUri: String): ByteArray
}
