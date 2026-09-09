package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.config.AsyncConfig
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

        runCatching {
            stickerImageStoragePorts.singlePort("스티커 이미지 저장소").deleteAll(event.imageKeys)
        }.onFailure {
            log.warn(
                "스티커 이미지 삭제 실패: stickerId={}, reason={}, imageKeys={}",
                event.stickerId,
                event.reason,
                event.imageKeys,
                it,
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(StickerImageCleanupEventListener::class.java)
    }
}
