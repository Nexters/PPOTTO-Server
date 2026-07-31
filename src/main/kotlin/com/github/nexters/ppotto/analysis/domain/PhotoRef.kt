package com.github.nexters.ppotto.analysis.domain

import java.util.UUID

data class PhotoRef(
    val photoId: UUID,
    val gcsUri: String,
    val mimeType: String,
)
