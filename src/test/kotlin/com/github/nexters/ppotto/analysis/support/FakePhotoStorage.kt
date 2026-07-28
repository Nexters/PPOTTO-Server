package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.domain.PhotoUploadTarget

class FakePhotoStorage : PhotoStorage {
    private val existingKeys = mutableSetOf<String>()

    override fun issueUploadUrls(targets: List<PhotoUploadTarget>): List<String> =
        targets.map {
            existingKeys += it.objectKey
            "https://fake-signed-url/${it.objectKey}"
        }

    override fun existingObjectKeys(prefix: String): Set<String> = existingKeys.filter { it.startsWith(prefix) }.toSet()

    fun markMissing(objectKey: String) {
        existingKeys -= objectKey
    }

    fun markUploaded(objectKey: String) {
        existingKeys += objectKey
    }
}
