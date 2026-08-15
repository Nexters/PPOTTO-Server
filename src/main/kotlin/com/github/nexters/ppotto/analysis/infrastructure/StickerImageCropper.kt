package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.global.error.BusinessException
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max

@Component
class StickerImageCropper {
    fun cropTransparentPadding(pngBytes: ByteArray): ByteArray {
        val image = readImage(pngBytes)
        if (!image.colorModel.hasAlpha()) {
            return pngBytes
        }

        val bounds = alphaBounds(image)
        val padding = ceil(max(bounds.width, bounds.height) * PADDING_RATIO).toInt()
        val crop = bounds.expand(padding, image.width, image.height)
        val cropped = image.getSubimage(crop.left, crop.top, crop.width, crop.height)
        return writePng(cropped)
    }

    private fun readImage(pngBytes: ByteArray): BufferedImage =
        try {
            ImageIO.read(ByteArrayInputStream(pngBytes)) ?: throw cropFailed()
        } catch (e: IOException) {
            throw cropFailed(e)
        }

    private fun alphaBounds(image: BufferedImage): Bounds {
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y) ushr ALPHA_SHIFT
                if (alpha > TRANSPARENT_ALPHA) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }

        if (right < left || bottom < top) {
            throw cropFailed()
        }

        return Bounds(left, top, right, bottom)
    }

    private fun writePng(image: BufferedImage): ByteArray =
        try {
            ByteArrayOutputStream().use { output ->
                if (!ImageIO.write(image, PNG_FORMAT, output)) {
                    throw cropFailed()
                }
                output.toByteArray()
            }
        } catch (e: IOException) {
            throw cropFailed(e)
        }

    private fun cropFailed(cause: Throwable? = null) = BusinessException(AnalysisErrorCode.STICKER_BACKGROUND_REMOVAL_FAILED, cause = cause)

    private data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int = right - left + 1
        val height: Int = bottom - top + 1

        fun expand(
            padding: Int,
            imageWidth: Int,
            imageHeight: Int,
        ): Bounds {
            val nextLeft = (left - padding).coerceAtLeast(0)
            val nextTop = (top - padding).coerceAtLeast(0)
            val nextRight = (right + padding).coerceAtMost(imageWidth - 1)
            val nextBottom = (bottom + padding).coerceAtMost(imageHeight - 1)
            return Bounds(nextLeft, nextTop, nextRight, nextBottom)
        }
    }

    companion object {
        private const val PADDING_RATIO = 0.12
        private const val ALPHA_SHIFT = 24
        private const val TRANSPARENT_ALPHA = 0
        private const val PNG_FORMAT = "png"
    }
}
