package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.global.error.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class StickerImageCropperTest {
    private val cropper = StickerImageCropper()

    @Test
    fun `투명 배경 가운데 피사체가 있으면 12퍼센트 패딩을 남기고 자른다`() {
        val input =
            transparentImage(100, 100).also {
                fill(it, 30, 40, 20, 10, Color(255, 0, 0, 255).rgb)
            }

        val output = read(cropper.cropTransparentPadding(write(input)))

        output.width shouldBe 26
        output.height shouldBe 16
        alpha(output, 3, 3) shouldBe 255
        alpha(output, 2, 2) shouldBe 0
    }

    @Test
    fun `피사체가 가장자리에 붙어 있으면 이미지 경계 안에서 패딩을 제한한다`() {
        val input =
            transparentImage(50, 50).also {
                fill(it, 0, 0, 10, 10, Color(0, 255, 0, 255).rgb)
            }

        val output = read(cropper.cropTransparentPadding(write(input)))

        output.width shouldBe 12
        output.height shouldBe 12
        alpha(output, 0, 0) shouldBe 255
        alpha(output, 11, 11) shouldBe 0
    }

    @Test
    fun `반투명 픽셀도 피사체 영역에 포함한다`() {
        val input =
            transparentImage(20, 20).also {
                it.setRGB(5, 5, Color(0, 0, 255, 33).rgb)
                it.setRGB(10, 10, Color(0, 0, 255, 255).rgb)
            }

        val output = read(cropper.cropTransparentPadding(write(input)))

        output.width shouldBe 8
        output.height shouldBe 8
        alpha(output, 1, 1) shouldBe 33
        alpha(output, 6, 6) shouldBe 255
    }

    @Test
    fun `이미지 가장자리의 약한 알파 노이즈는 피사체 영역에서 제외한다`() {
        val input =
            transparentImage(100, 100).also {
                it.setRGB(0, 0, Color(0, 0, 255, 1).rgb)
                fill(it, 40, 40, 10, 10, Color(255, 0, 0, 255).rgb)
            }

        val output = read(cropper.cropTransparentPadding(write(input)))

        output.width shouldBe 14
        output.height shouldBe 14
        alpha(output, 0, 0) shouldBe 0
        alpha(output, 2, 2) shouldBe 255
    }

    @Test
    fun `강한 알파 픽셀이 없으면 약한 반투명 픽셀 기준으로 자른다`() {
        val input =
            transparentImage(20, 20).also {
                it.setRGB(10, 10, Color(0, 0, 255, 1).rgb)
            }

        val output = read(cropper.cropTransparentPadding(write(input)))

        output.width shouldBe 3
        output.height shouldBe 3
        alpha(output, 1, 1) shouldBe 1
    }

    @Test
    fun `전부 투명한 PNG는 배경 제거 실패 예외로 처리한다`() {
        val exception =
            shouldThrow<BusinessException> {
                cropper.cropTransparentPadding(write(transparentImage(10, 10)))
            }

        exception.errorCode shouldBe AnalysisErrorCode.STICKER_BACKGROUND_REMOVAL_FAILED
    }

    @Test
    fun `알파 채널이 없는 이미지는 원본 바이트를 반환한다`() {
        val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
        val bytes = write(image)

        cropper.cropTransparentPadding(bytes) shouldBe bytes
    }

    private fun transparentImage(
        width: Int,
        height: Int,
    ) = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    private fun fill(
        image: BufferedImage,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        rgb: Int,
    ) {
        for (y in startY until startY + height) {
            for (x in startX until startX + width) {
                image.setRGB(x, y, rgb)
            }
        }
    }

    private fun write(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }

    private fun read(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

    private fun alpha(
        image: BufferedImage,
        x: Int,
        y: Int,
    ): Int = image.getRGB(x, y) ushr 24
}
