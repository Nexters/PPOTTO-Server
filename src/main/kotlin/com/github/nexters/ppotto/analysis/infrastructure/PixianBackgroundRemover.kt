package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.global.config.PixianProperties
import com.github.nexters.ppotto.global.error.BusinessException
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
        val response = requestRemoveBackground(imageBytes, mimeType)
        val creditsCharged = response.headers.getFirst(CREDITS_CHARGED_HEADER)
        log.info("pixian background removal succeeded: creditsCharged={}, test={}", creditsCharged, pixianProperties.testMode)

        return requireResponseBody(response)
    }

    private fun requestRemoveBackground(
        imageBytes: ByteArray,
        mimeType: String,
    ): ResponseEntity<ByteArray> {
        val resource = namedResource(imageBytes, mimeType)
        return try {
            callRemoveBackground(resource)
        } catch (e: RestClientResponseException) {
            if (!e.statusCode.is5xxServerError) {
                log.warn(
                    "pixian background removal failed: status={}, body={}",
                    e.statusCode,
                    e.getResponseBodyAsString(),
                )
                throw backgroundRemovalFailed(e)
            }
            log.warn(
                "pixian background removal failed, retrying once: status={}, body={}",
                e.statusCode,
                e.getResponseBodyAsString(),
            )
            retryRemoveBackground(resource)
        } catch (e: RestClientException) {
            log.warn("pixian background removal request failed, retrying once", e)
            retryRemoveBackground(resource)
        }
    }

    private fun retryRemoveBackground(resource: Resource): ResponseEntity<ByteArray> =
        try {
            callRemoveBackground(resource)
        } catch (e: RestClientResponseException) {
            log.warn(
                "pixian background removal retry failed: status={}, body={}",
                e.statusCode,
                e.getResponseBodyAsString(),
            )
            throw backgroundRemovalFailed(e)
        } catch (e: RestClientException) {
            log.warn("pixian background removal retry failed", e)
            throw backgroundRemovalFailed(e)
        }

    private fun callRemoveBackground(resource: Resource): ResponseEntity<ByteArray> =
        pixianApi.removeBackground(
            pixianProperties.removeBackgroundUri,
            basicAuthorization(),
            resource,
            pixianProperties.testMode.toString(),
            OUTPUT_FORMAT_PNG,
        )

    private fun requireResponseBody(response: ResponseEntity<ByteArray>) = response.body ?: throw backgroundRemovalFailed()

    private fun backgroundRemovalFailed(cause: Throwable? = null) =
        BusinessException(AnalysisErrorCode.STICKER_BACKGROUND_REMOVAL_FAILED, cause = cause)

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
