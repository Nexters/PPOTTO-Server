package com.github.nexters.ppotto.analysis.infrastructure

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class SourcePhotoStickerGeneratorTest :
    BehaviorSpec({
        Given("sourcePhoto 기반 스티커 생성기가") {
            val sourceBytes = byteArrayOf(1, 2, 3, 4)
            val removedBackgroundBytes = transparentPaddedPng()
            val sourcePhotoImageReader = FakeSourcePhotoImageReader(sourceBytes)
            val stickerBackgroundRemover = FakeStickerBackgroundRemover(removedBackgroundBytes)
            val generator =
                SourcePhotoStickerGenerator(
                    sourcePhotoImageReader = sourcePhotoImageReader,
                    stickerBackgroundRemover = stickerBackgroundRemover,
                    stickerImageCropper = StickerImageCropper(),
                )

            When("스티커를 생성하면") {
                val stickerBytes =
                    generator.generate(
                        sourceGcsUri = "gs://ppotto-test/photos/analysis/photo.jpg",
                        sourceMimeType = "image/jpeg",
                        targetSubject = "빨간 컵",
                    )

                Then("원본 sourcePhoto 바이트와 mimeType을 배경 제거기에 전달하고 crop된 PNG를 반환한다") {
                    sourcePhotoImageReader.requestedUri shouldBe "gs://ppotto-test/photos/analysis/photo.jpg"
                    stickerBackgroundRemover.receivedBytes shouldBe sourceBytes
                    stickerBackgroundRemover.receivedMimeType shouldBe "image/jpeg"
                    val stickerImage = ImageIO.read(ByteArrayInputStream(stickerBytes))
                    stickerImage.width shouldBeGreaterThan 0
                    stickerImage.height shouldBeGreaterThan 0
                }
            }
        }
    })

private class FakeSourcePhotoImageReader(
    private val sourceBytes: ByteArray,
) : SourcePhotoImageReader {
    lateinit var requestedUri: String

    override fun read(sourceGcsUri: String): ByteArray {
        requestedUri = sourceGcsUri
        return sourceBytes
    }
}

private class FakeStickerBackgroundRemover(
    private val removedBackgroundBytes: ByteArray,
) : StickerBackgroundRemover {
    lateinit var receivedBytes: ByteArray
    lateinit var receivedMimeType: String

    override fun removeBackground(
        imageBytes: ByteArray,
        mimeType: String,
    ): ByteArray {
        receivedBytes = imageBytes
        receivedMimeType = mimeType
        return removedBackgroundBytes
    }
}

private fun transparentPaddedPng(): ByteArray {
    val image = BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(1, 1, Color.RED.rgb)
    return ByteArrayOutputStream().use {
        ImageIO.write(image, "png", it)
        it.toByteArray()
    }
}
