package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.global.error.BusinessException
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URISyntaxException

interface SourcePhotoImageReader {
    fun read(sourceGcsUri: String): ByteArray
}

@Component
class GcsSourcePhotoImageReader(
    private val storage: Storage,
) : SourcePhotoImageReader {
    override fun read(sourceGcsUri: String): ByteArray {
        val source = parseSourceGcsUri(sourceGcsUri)
        return try {
            storage.readAllBytes(BlobId.of(source.bucket, source.objectKey))
        } catch (e: StorageException) {
            log.warn("source photo read failed: sourceGcsUri={}", sourceGcsUri, e)
            throw sourcePhotoReadFailed(e)
        } catch (e: IllegalArgumentException) {
            log.warn("source photo read failed: sourceGcsUri={}", sourceGcsUri, e)
            throw sourcePhotoReadFailed(e)
        }
    }

    private fun parseSourceGcsUri(sourceGcsUri: String): SourceGcsObject {
        val uri =
            try {
                URI(sourceGcsUri)
            } catch (e: URISyntaxException) {
                throw sourcePhotoReadFailed(e)
            }
        val objectKey = uri.path.removePrefix("/")
        if (uri.scheme != GCS_SCHEME || uri.host.isNullOrBlank() || objectKey.isBlank()) {
            throw sourcePhotoReadFailed()
        }
        return SourceGcsObject(uri.host, objectKey)
    }

    private fun sourcePhotoReadFailed(cause: Throwable? = null) =
        BusinessException(AnalysisErrorCode.STICKER_BACKGROUND_REMOVAL_FAILED, cause = cause)

    private data class SourceGcsObject(
        val bucket: String,
        val objectKey: String,
    )

    companion object {
        private val log = LoggerFactory.getLogger(GcsSourcePhotoImageReader::class.java)
        private const val GCS_SCHEME = "gs"
    }
}
