package com.github.nexters.ppotto.analysis.infrastructure.image

import com.github.nexters.ppotto.analysis.application.port.SourcePhotoImageReader
import com.github.nexters.ppotto.analysis.application.port.StickerBackgroundRemover
import com.github.nexters.ppotto.analysis.infrastructure.image.SourcePhotoStickerGenerator
import com.github.nexters.ppotto.analysis.infrastructure.image.StickerImageCropper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private const val CANVAS_SIZE = 100
private const val SUBJECT_LEFT = 45
private const val SUBJECT_SIZE = 10

// 피사체 10px의 12% 여백 = ceil(1.2) = 2px, 좌우/상하 각각 붙어 10 + 2 + 2 = 14px.
private const val EXPECTED_CROP_SIZE = 14
private const val EXPECTED_PADDING = 2

class SourcePhotoStickerGeneratorTest :
    BehaviorSpec({
        Given("배경이 모두 투명하고 가운데 10px 정사각형만 불투명한 100px PNG를 배경 제거기가 돌려줄 때") {
            val sourceBytes = byteArrayOf(1, 2, 3, 4)
            val sourcePhotoImageReader = FakeSourcePhotoImageReader(sourceBytes)
            val stickerBackgroundRemover = FakeStickerBackgroundRemover(transparentPaddedPng())
            val generator =
                SourcePhotoStickerGenerator(
                    sourcePhotoImageReader = sourcePhotoImageReader,
                    stickerBackgroundRemover = stickerBackgroundRemover,
                    stickerImageCropper = StickerImageCropper(),
                )

            When("스티커를 생성하면") {
                val stickerBytes =
                    generator.generate(
                        sourceUri = "gs://ppotto-test/photos/analysis/photo.jpg",
                        sourceMimeType = "image/jpeg",
                        targetSubject = "빨간 컵",
                    )
                val stickerImage = ImageIO.read(ByteArrayInputStream(stickerBytes))

                Then("원본 사진 바이트와 mimeType을 그대로 배경 제거기에 넘긴다") {
                    sourcePhotoImageReader.requestedUri shouldBe "gs://ppotto-test/photos/analysis/photo.jpg"
                    stickerBackgroundRemover.receivedBytes shouldBe sourceBytes
                    stickerBackgroundRemover.receivedMimeType shouldBe "image/jpeg"
                }

                Then("투명 여백을 잘라내 피사체 10px에 여백 2px씩 붙인 14x14 PNG를 반환한다") {
                    stickerImage.width shouldBe EXPECTED_CROP_SIZE
                    stickerImage.height shouldBe EXPECTED_CROP_SIZE
                }

                Then("잘라낸 영역은 피사체를 감싼 위치라 여백만큼 안쪽에 불투명 픽셀이 남는다") {
                    stickerImage.getRGB(EXPECTED_PADDING, EXPECTED_PADDING) shouldBe Color.RED.rgb
                    stickerImage.getRGB(0, 0) shouldBe 0
                }
            }
        }
    })

private class FakeSourcePhotoImageReader(
    private val sourceBytes: ByteArray,
) : SourcePhotoImageReader {
    lateinit var requestedUri: String

    override fun read(sourceUri: String): ByteArray {
        requestedUri = sourceUri
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
    val image = BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB)
    for (y in SUBJECT_LEFT until SUBJECT_LEFT + SUBJECT_SIZE) {
        for (x in SUBJECT_LEFT until SUBJECT_LEFT + SUBJECT_SIZE) {
            image.setRGB(x, y, Color.RED.rgb)
        }
    }
    return ByteArrayOutputStream().use {
        ImageIO.write(image, "png", it)
        it.toByteArray()
    }
}
