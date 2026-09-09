package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.config.AsyncConfig
import com.github.nexters.ppotto.global.logging.bestEffort
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import com.github.nexters.ppotto.sticker.application.port.singlePort
import com.github.nexters.ppotto.sticker.domain.StickerImageDeletionRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class StickerImageCleanupEventListener(
    private val stickerImageStoragePorts: List<StickerImageStoragePort>,
) {
    @Async(AsyncConfig.STICKER_IMAGE_CLEANUP_TASK_EXECUTOR)
    @EventListener
    fun handle(event: StickerImageDeletionRequestedEvent) {
        if (event.imageKeys.isEmpty()) {
            return
        }

        bestEffort(
            log,
            "스티커 이미지 삭제 stickerId=${event.stickerId} reason=${event.reason} imageKeys=${event.imageKeys}",
        ) {
            stickerImageStoragePorts.singlePort("스티커 이미지 저장소").deleteAll(event.imageKeys)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(StickerImageCleanupEventListener::class.java)
    }
}
