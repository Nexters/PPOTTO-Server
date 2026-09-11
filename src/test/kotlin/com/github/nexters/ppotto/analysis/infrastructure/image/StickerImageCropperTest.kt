package com.github.nexters.ppotto.analysis.infrastructure.image

import com.github.nexters.ppotto.analysis.infrastructure.image.StickerImageCropper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO

class StickerImageCropperTest :
    BehaviorSpec({
        val cropper = StickerImageCropper()

        Given("투명 배경 가운데 20x10 피사체가 있는 100x100 PNG가") {
            val input = transparentImage(100, 100)
            fill(input, 30, 40, 20, 10, Color(255, 0, 0, 255).rgb)

            When("투명 여백을 자르면") {
                val output = read(cropper.cropTransparentPadding(write(input)))

                Then("피사체 긴 변의 12퍼센트만 여백으로 남긴다") {
                    output.width shouldBe 26
                    output.height shouldBe 16
                }

                Then("피사체 픽셀은 남고 여백은 투명하게 유지된다") {
                    alpha(output, 3, 3) shouldBe 255
                    alpha(output, 2, 2) shouldBe 0
                }
            }
        }

        Given("피사체가 좌상단 모서리에 붙어 있는 50x50 PNG가") {
            val input = transparentImage(50, 50)
            fill(input, 0, 0, 10, 10, Color(0, 255, 0, 255).rgb)

            When("투명 여백을 자르면") {
                val output = read(cropper.cropTransparentPadding(write(input)))

                Then("여백을 원본 이미지 경계 안으로 제한한다") {
                    output.width shouldBe 12
                    output.height shouldBe 12
                    alpha(output, 0, 0) shouldBe 255
                    alpha(output, 11, 11) shouldBe 0
                }
            }
        }

        Given("피사체 근처에 알파 9의 반투명 픽셀이 있는 20x20 PNG가") {
            val input = transparentImage(20, 20)
            input.setRGB(5, 5, Color(0, 0, 255, 9).rgb)
            input.setRGB(10, 10, Color(0, 0, 255, 255).rgb)

            When("투명 여백을 자르면") {
                val output = read(cropper.cropTransparentPadding(write(input)))

                Then("낮은 알파 픽셀도 피사체 영역에 포함한다") {
                    output.width shouldBe 8
                    output.height shouldBe 8
                    alpha(output, 1, 1) shouldBe 9
                    alpha(output, 6, 6) shouldBe 255
                }
            }
        }

        Given("피사체에서 멀리 떨어진 모서리에만 알파 1 노이즈가 있는 100x100 PNG가") {
            val input = transparentImage(100, 100)
            input.setRGB(0, 0, Color(0, 0, 255, 1).rgb)
            fill(input, 40, 40, 10, 10, Color(255, 0, 0, 255).rgb)

            When("투명 여백을 자르면") {
                val output = read(cropper.cropTransparentPadding(write(input)))

                Then("먼 약한 알파 노이즈는 피사체 영역에서 제외한다") {
                    output.width shouldBe 14
                    output.height shouldBe 14
                    alpha(output, 0, 0) shouldBe 0
                    alpha(output, 2, 2) shouldBe 255
                }
            }
        }

        Given("피사체 근처와 먼 모서리 양쪽에 알파 1 픽셀이 있는 100x100 PNG가") {
            val input = transparentImage(100, 100)
            input.setRGB(0, 0, Color(0, 0, 255, 1).rgb)
            input.setRGB(30, 40, Color(0, 0, 255, 1).rgb)
            fill(input, 40, 40, 10, 10, Color(255, 0, 0, 255).rgb)

            When("투명 여백을 자르면") {
                val output = read(cropper.cropTransparentPadding(write(input)))

                Then("피사체 근처의 약한 알파 디테일만 포함하고 먼 노이즈는 제외한다") {
                    output.width shouldBe 26
                    output.height shouldBe 16
                    alpha(output, 3, 3) shouldBe 1
                    alpha(output, 13, 3) shouldBe 255
                    alpha(output, 0, 0) shouldBe 0
                }
            }
        }

        Given("전체 캔버스가 알파 1로 덮인 100x100 PNG가") {
            val input = transparentImage(100, 100)
            fill(input, 0, 0, 100, 100, Color(0, 0, 255, 1).rgb)
            fill(input, 40, 40, 10, 10, Color(255, 0, 0, 255).rgb)

            When("투명 여백을 자르면") {
                val output = read(cropper.cropTransparentPadding(write(input)))

                Then("희미한 잔여물을 피사체 전체 크기로 보지 않는다") {
                    output.width shouldBe 54
                    output.height shouldBe 54
                    alpha(output, 0, 0) shouldBe 1
                    alpha(output, 22, 22) shouldBe 255
                }
            }
        }

        Given("알파 1 픽셀 하나만 있는 20x20 PNG가") {
            val input = transparentImage(20, 20)
            input.setRGB(10, 10, Color(0, 0, 255, 1).rgb)

            When("투명 여백을 자르면") {
                val output = read(cropper.cropTransparentPadding(write(input)))

                Then("강한 알파 픽셀이 없으면 약한 반투명 픽셀을 기준으로 자른다") {
                    output.width shouldBe 3
                    output.height shouldBe 3
                    alpha(output, 1, 1) shouldBe 1
                }
            }
        }

        Given("전부 투명한 10x10 PNG가") {
            val input = transparentImage(10, 10)

            When("투명 여백을 자르면") {
                Then("피사체를 찾지 못해 이미지 처리 실패로 끝난다") {
                    shouldThrow<IOException> { cropper.cropTransparentPadding(write(input)) }
                }
            }
        }

        Given("알파 채널이 없는 10x10 이미지가") {
            val bytes = write(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB))

            When("투명 여백을 자르면") {
                Then("자를 여백이 없어 원본 바이트를 그대로 반환한다") {
                    cropper.cropTransparentPadding(bytes) shouldBe bytes
                }
            }
        }
    })

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
