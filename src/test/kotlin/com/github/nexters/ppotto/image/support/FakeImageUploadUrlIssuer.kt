package com.github.nexters.ppotto.image.support

import com.github.nexters.ppotto.image.domain.ImageUploadUrlIssuer

class FakeImageUploadUrlIssuer : ImageUploadUrlIssuer {
    override fun issue(
        objectKey: String,
        contentType: String,
    ): String = "https://fake-signed-url/$objectKey"
}
