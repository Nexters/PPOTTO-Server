package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.config.PixianProperties
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.StickerBackgroundRemover
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.util.Base64

@Component
class PixianBackgroundRemover(
    private val pixianApi: PixianApi,
    private val pixianProperties: PixianProperties,
) : StickerBackgroundRemover {
    override fun removeBackground(
        imageBytes: ByteArray,
        mimeType: String,
    ): ByteArray {
        val resource = namedResource(imageBytes, mimeType)
        val response = retryOnceOn5xx { callRemoveBackground(resource) }
        val creditsCharged = response.headers.getFirst(CREDITS_CHARGED_HEADER)
        log.info("pixian background removal succeeded: creditsCharged={}, test={}", creditsCharged, pixianProperties.testMode)

        return response.body ?: throw RestClientException("pixian 배경 제거 응답 본문이 비어 있습니다.")
    }

    private fun <T> retryOnceOn5xx(block: () -> T): T =
        try {
            block()
        } catch (e: RestClientResponseException) {
            if (!e.statusCode.is5xxServerError) throw e
            log.warn("pixian background removal failed, retrying once: status={}, body={}", e.statusCode, e.getResponseBodyAsString())
            block()
        } catch (e: RestClientException) {
            log.warn("pixian background removal request failed, retrying once", e)
            block()
        }

    private fun callRemoveBackground(resource: Resource): ResponseEntity<ByteArray> =
        pixianApi.removeBackground(
            pixianProperties.removeBackgroundUri,
            basicAuthorization(),
            resource,
            pixianProperties.testMode.toString(),
            OUTPUT_FORMAT_PNG,
        )

    private fun basicAuthorization(): String {
        val credentials = "${pixianProperties.apiId}:${pixianProperties.apiSecret}"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    private fun namedResource(
        imageBytes: ByteArray,
        mimeType: String,
    ): Resource {
        val extension = resolveExtension(mimeType)
        return object : ByteArrayResource(imageBytes) {
            override fun getFilename() = "source-image.$extension"
        }
    }

    private fun resolveExtension(mimeType: String): String {
        val matched = PhotoContentType.entries.firstOrNull { it.mimeType == mimeType }
        if (matched == null) {
            log.warn("pixian background removal received unknown mimeType, falling back to default extension: mimeType={}", mimeType)
            return DEFAULT_EXTENSION
        }
        return matched.extension
    }

    companion object {
        private val log = LoggerFactory.getLogger(PixianBackgroundRemover::class.java)
        private const val OUTPUT_FORMAT_PNG = "png"
        private const val DEFAULT_EXTENSION = "png"
        private const val CREDITS_CHARGED_HEADER = "X-Credits-Charged"
    }
}
