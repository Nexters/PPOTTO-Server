package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import com.github.nexters.ppotto.sticker.domain.StickerImageDeletionReason
import com.github.nexters.ppotto.sticker.domain.StickerImageDeletionRequestedEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import java.util.UUID

class StickerImageCleanupEventListenerTest :
    BehaviorSpec({
        Given("이미지 삭제가 실패하는 저장소가 연결된 상태에서") {
            val storage = FailingStickerImageStorage()
            val listener = StickerImageCleanupEventListener(listOf(storage))

            When("이미지 삭제 이벤트를 처리하면") {
                val thrown = runCatching { listener.handle(deletionEvent(listOf("stickers/failed.png"))) }.exceptionOrNull()

                Then("삭제 실패를 요청 결과로 전파하지 않는다") {
                    thrown.shouldBeNull()
                }

                Then("삭제는 실제로 시도한다") {
                    storage.requestedImageKeys shouldContainExactly listOf("stickers/failed.png")
                }
            }
        }

        Given("삭제 대상 이미지 키가 없는 이벤트에서") {
            val storage = FailingStickerImageStorage()
            val listener = StickerImageCleanupEventListener(listOf(storage))

            When("이미지 삭제 이벤트를 처리하면") {
                listener.handle(deletionEvent(emptyList()))

                Then("저장소를 호출하지 않는다") {
                    storage.requestedImageKeys.shouldBeEmpty()
                }
            }
        }
    })

private fun deletionEvent(imageKeys: List<String>) =
    StickerImageDeletionRequestedEvent(
        stickerId = StickerId(UUID.randomUUID()),
        imageKeys = imageKeys,
        reason = StickerImageDeletionReason.REGENERATED_IMAGE_REPLACED,
    )

private class FailingStickerImageStorage : StickerImageStoragePort {
    val requestedImageKeys = mutableListOf<String>()

    override fun issueReadUrls(imageKeys: Collection<String>): Map<String, String> = emptyMap()

    override fun deleteAll(imageKeys: Collection<String>) {
        requestedImageKeys += imageKeys
        error("오브젝트 스토리지 삭제 실패")
    }
}
