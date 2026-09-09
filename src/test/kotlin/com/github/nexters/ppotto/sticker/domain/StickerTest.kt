package com.github.nexters.ppotto.sticker.domain

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.support.stickerLayout
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class StickerTest :
    BehaviorSpec({
        Given("이미지 스티커가 생성된 상태에서") {
            val sticker = imageSticker()

            When("제목과 배치를 변경하면") {
                sticker.updateLayout(stickerLayout(title = "새 제목"))

                Then("제목과 모든 배치값이 변경된다") {
                    sticker.title shouldBe "새 제목"
                    sticker.posX shouldBe 10.0
                    sticker.posY shouldBe 20.0
                    sticker.scale shouldBe 0.8
                    sticker.rotation shouldBe 5.0
                    sticker.zIndex shouldBe 2
                    sticker.badgeOffsetX shouldBe 3.0
                    sticker.badgeOffsetY shouldBe 4.0
                    sticker.badgeRotation shouldBe 6.0
                }
            }

            When("두 번 열람 처리하면") {
                val firstViewedAt = Instant.parse("2026-07-30T01:00:00Z")
                sticker.markViewed(firstViewedAt)
                sticker.markViewed(Instant.parse("2026-07-30T02:00:00Z"))

                Then("최초 열람 시각을 유지한다") {
                    sticker.viewedAt shouldBe firstViewedAt
                }
            }

            When("두 번 삭제 처리하면") {
                val firstDeletedAt = Instant.parse("2026-07-30T03:00:00Z")
                sticker.delete(firstDeletedAt)
                sticker.delete(Instant.parse("2026-07-30T04:00:00Z"))

                Then("최초 삭제 시각을 유지한다") {
                    sticker.deletedAt shouldBe firstDeletedAt
                }
            }
        }

        Given("이미지 스티커의 제목을 규칙 밖의 값으로 바꿀 때") {
            When("15자를 초과한 제목으로 이름을 바꾸면") {
                val target = imageSticker()
                val exception = shouldThrow<InvalidInputException> { target.rename("가".repeat(16)) }

                Then("클라이언트 입력 등급인 COMMON-001로 거부한다") {
                    exception.errorCode.code shouldBe "COMMON-001"
                }

                Then("제목은 그대로 남는다") {
                    target.title shouldBe "이미지"
                }
            }

            When("공백뿐인 제목으로 이름을 바꾸면") {
                val target = imageSticker()
                shouldThrow<InvalidInputException> { target.rename(" ") }

                Then("제목은 그대로 남는다") {
                    target.title shouldBe "이미지"
                }
            }

            When("잘못된 제목이 섞인 배치로 변경하면") {
                val target = imageSticker()
                shouldThrow<InvalidInputException> { target.updateLayout(stickerLayout(title = " ")) }

                Then("제목은 그대로 남는다") {
                    target.title shouldBe "이미지"
                }
            }
        }

        Given("DB 행이나 어댑터 출력이 저장 규칙을 벗어났을 때") {
            When("hex 형식이 아닌 메인 컬러로 스티커를 복원하면") {
                Then("구현 버그 등급인 IllegalArgumentException 을 던진다") {
                    shouldThrow<IllegalArgumentException> { imageSticker(mainColor = "red") }
                }
            }

            When("이미지 키가 빈 이미지 스티커를 복원하면") {
                Then("구현 버그 등급인 IllegalArgumentException 을 던진다") {
                    shouldThrow<IllegalArgumentException> { imageSticker(imageKey = " ") }
                }
            }

            When("hex 형식이 아닌 메인 컬러로 재생성하면") {
                Then("구현 버그 등급인 IllegalArgumentException 을 던진다") {
                    shouldThrow<IllegalArgumentException> {
                        imageSticker().regenerateSticker(PhotoId(UUID.randomUUID()), "stickers/new.png", "#GGGGGG")
                    }
                }
            }

            When("이미지 키가 빈 채로 재생성하면") {
                Then("구현 버그 등급인 IllegalArgumentException 을 던진다") {
                    shouldThrow<IllegalArgumentException> {
                        imageSticker().regenerateSticker(PhotoId(UUID.randomUUID()), " ", "#FF6B6B")
                    }
                }
            }
        }

        Given("스티커 생성값이 유효하지 않을 때") {
            When("이미지 키 없이 이미지 스티커를 생성하면") {
                Then("잘못된 입력 예외를 던진다") {
                    shouldThrow<InvalidInputException> {
                        StickerCreation(
                            type = StickerType.IMAGE,
                            title = "이미지",
                            summary = "한 줄 요약",
                            sourcePhotoId = PhotoId(UUID.randomUUID()),
                            imageKey = null,
                            textContent = null,
                            mainColor = "#FF6B6B",
                        )
                    }
                }
            }

            When("15자를 초과한 제목으로 생성하면") {
                Then("잘못된 입력 예외를 던진다") {
                    shouldThrow<InvalidInputException> {
                        StickerCreation(
                            type = StickerType.TEXT,
                            title = "가".repeat(16),
                            summary = "한 줄 요약",
                            sourcePhotoId = null,
                            imageKey = null,
                            textContent = "텍스트",
                            mainColor = "#FF6B6B",
                        )
                    }
                }
            }

            When("100자를 초과한 한 줄 요약으로 생성하면") {
                Then("잘못된 입력 예외를 던진다") {
                    shouldThrow<InvalidInputException> {
                        StickerCreation(
                            type = StickerType.TEXT,
                            title = "텍스트",
                            summary = "가".repeat(101),
                            sourcePhotoId = null,
                            imageKey = null,
                            textContent = "텍스트",
                            mainColor = "#FF6B6B",
                        )
                    }
                }
            }

            When("공백뿐인 한 줄 요약으로 생성하면") {
                Then("잘못된 입력 예외를 던진다") {
                    shouldThrow<InvalidInputException> {
                        StickerCreation(
                            type = StickerType.TEXT,
                            title = "텍스트",
                            summary = " ",
                            sourcePhotoId = null,
                            imageKey = null,
                            textContent = "텍스트",
                            mainColor = "#FF6B6B",
                        )
                    }
                }
            }

            When("hex 형식이 아닌 메인 컬러로 생성하면") {
                Then("잘못된 입력 예외를 던진다") {
                    shouldThrow<InvalidInputException> {
                        StickerCreation(
                            type = StickerType.TEXT,
                            title = "텍스트",
                            summary = "한 줄 요약",
                            sourcePhotoId = null,
                            imageKey = null,
                            textContent = "텍스트",
                            mainColor = "red",
                        )
                    }
                }
            }
        }
    })

private fun imageSticker(
    imageKey: String? = "stickers/image.png",
    mainColor: String = "#FF6B6B",
) = Sticker(
    id = StickerId(UUID.randomUUID()),
    analysisId = AnalysisId(UUID.randomUUID()),
    boardId = BoardId(UUID.randomUUID()),
    type = StickerType.IMAGE,
    title = "이미지",
    summary = "웃기고 귀여우면 일단 주워요",
    viewedAt = null,
    sourcePhotoId = PhotoId(UUID.randomUUID()),
    imageKey = imageKey,
    textContent = null,
    mainColor = mainColor,
    posX = 0.0,
    posY = 0.0,
    scale = 1.0,
    rotation = 0.0,
    zIndex = 0,
    badgeOffsetX = 0.0,
    badgeOffsetY = 0.0,
    badgeRotation = 0.0,
    createdAt = Instant.parse("2026-07-30T00:00:00Z"),
    updatedAt = Instant.parse("2026-07-30T00:00:00Z"),
    deletedAt = null,
)
