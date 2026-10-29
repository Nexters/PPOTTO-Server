package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.support.validWebp
import com.github.nexters.ppotto.global.error.InvalidInputException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BoardInvitationPreviewImageTest :
    BehaviorSpec({
        Given("허용 범위의 WebP 이미지가") {
            val bytes = validWebp()

            When("검증되면") {
                val image = BoardInvitationPreviewImage.from(bytes)

                Then("원본 바이트를 보존한다") {
                    image.bytes shouldBe bytes
                }
            }
        }

        Given("WebP가 아닌 바이트가") {
            When("검증되면") {
                val exception = shouldThrow<InvalidInputException> { BoardInvitationPreviewImage.from(byteArrayOf(1, 2, 3)) }

                Then("BOARD-007로 거부한다") {
                    exception.errorCode shouldBe BoardErrorCode.INVALID_INVITATION_PREVIEW
                }
            }
        }

        Given("VP8X 크기 정보만 있고 실제 이미지 데이터가 잘못된 WebP가") {
            When("검증되면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        BoardInvitationPreviewImage.from(malformedExtendedWebp())
                    }

                Then("BOARD-007로 거부한다") {
                    exception.errorCode shouldBe BoardErrorCode.INVALID_INVITATION_PREVIEW
                }
            }
        }

        Given("최대 해상도를 넘는 WebP 이미지가") {
            When("검증되면") {
                val exception = shouldThrow<InvalidInputException> { BoardInvitationPreviewImage.from(validWebp(width = 625)) }

                Then("BOARD-007로 거부한다") {
                    exception.errorCode shouldBe BoardErrorCode.INVALID_INVITATION_PREVIEW
                }
            }
        }

        Given("1 MiB를 넘는 WebP 이미지가") {
            val oversized = validWebp() + ByteArray(BoardInvitationPreviewImage.MAX_SIZE_BYTES)

            When("검증되면") {
                val exception = shouldThrow<InvalidInputException> { BoardInvitationPreviewImage.from(oversized) }

                Then("BOARD-007로 거부한다") {
                    exception.errorCode shouldBe BoardErrorCode.INVALID_INVITATION_PREVIEW
                }
            }
        }
    })

private fun malformedExtendedWebp(): ByteArray {
    val extendedPayload = byteArrayOf(0, 0, 0, 0, 0x6F, 0x02, 0, 0x1B, 0x02, 0)
    val body =
        "WEBP".toByteArray() +
            "VP8X".toByteArray() +
            littleEndian(extendedPayload.size) +
            extendedPayload +
            "VP8 ".toByteArray() +
            littleEndian(0)
    return "RIFF".toByteArray() + littleEndian(body.size) + body
}

private fun littleEndian(value: Int): ByteArray =
    byteArrayOf(
        value.toByte(),
        (value shr 8).toByte(),
        (value shr 16).toByte(),
        (value shr 24).toByte(),
    )
