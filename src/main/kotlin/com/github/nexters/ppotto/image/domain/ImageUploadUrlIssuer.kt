package com.github.nexters.ppotto.image.domain

interface ImageUploadUrlIssuer {
    fun issue(
        objectKey: String,
        contentType: String,
    ): String
}
