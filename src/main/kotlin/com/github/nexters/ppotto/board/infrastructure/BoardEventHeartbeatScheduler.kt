package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.application.BoardEventBroker
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class BoardEventHeartbeatScheduler(
    private val boardEventBroker: BoardEventBroker,
) {
    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MILLIS)
    fun heartbeat() {
        boardEventBroker.heartbeat()
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MILLIS = 15_000L
    }
}
