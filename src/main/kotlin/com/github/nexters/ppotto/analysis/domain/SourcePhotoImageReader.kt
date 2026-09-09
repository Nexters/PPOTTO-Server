package com.github.nexters.ppotto.analysis.domain

interface SourcePhotoImageReader {
    fun read(sourceUri: String): ByteArray
}
