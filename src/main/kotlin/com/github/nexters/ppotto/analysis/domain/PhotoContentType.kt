package com.github.nexters.ppotto.analysis.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class PhotoContentType(
    @get:JsonValue val mimeType: String,
    val extension: String,
) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    HEIC("image/heic", "heic"),
}
