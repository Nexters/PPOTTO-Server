package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.InvalidInputException

enum class PhotoContentType(
    val mimeType: String,
    val extension: String,
) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    HEIC("image/heic", "heic"),
    ;

    companion object {
        val DEFAULT = JPEG

        fun fromMimeType(mimeType: String): PhotoContentType = entries.find { it.mimeType == mimeType } ?: throw InvalidInputException()
    }
}
