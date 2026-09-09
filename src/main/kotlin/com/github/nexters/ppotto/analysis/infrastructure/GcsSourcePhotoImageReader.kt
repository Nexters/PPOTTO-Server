package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.SourcePhotoImageReader
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Component
import java.net.URI

@Component
class GcsSourcePhotoImageReader(
    private val storage: Storage,
) : SourcePhotoImageReader {
    override fun read(sourceUri: String): ByteArray {
        val uri = URI.create(sourceUri)
        val objectKey =
            uri.path
                .orEmpty()
                .removePrefix("/")
        require(uri.scheme == GCS_SCHEME && !uri.host.isNullOrBlank() && objectKey.isNotBlank()) {
            "원본 사진 URI가 gs://{bucket}/{objectKey} 형식이 아닙니다: $sourceUri"
        }
        return storage.readAllBytes(BlobId.of(uri.host, objectKey))
    }

    companion object {
        private const val GCS_SCHEME = "gs"
    }
}
