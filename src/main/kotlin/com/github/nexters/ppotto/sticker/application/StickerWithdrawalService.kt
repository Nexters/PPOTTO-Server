package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import com.github.nexters.ppotto.sticker.application.port.singlePort
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class StickerWithdrawalService(
    private val stickerRepository: StickerRepository,
    private val stickerCommandRepository: StickerCommandRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    private val imageStoragePorts: List<StickerImageStoragePort>,
    private val transactionTemplate: TransactionTemplate,
) {
    fun deleteAllByBoardIds(boardIds: Collection<BoardId>) {
        val targets = stickerRepository.findDeletionTargetsByBoardIds(boardIds)
        if (targets.isEmpty()) {
            return
        }

        imageStoragePorts.singlePort("스티커 이미지 저장소").deleteAll(targets.mapNotNull { it.imageKey })

        val stickerIds = targets.map { it.id }
        transactionTemplate.executeWithoutResult {
            stickerRecapRepository.deleteByStickerIds(stickerIds)
            stickerCommandRepository.hardDeleteByIds(stickerIds)
        }
    }
}
