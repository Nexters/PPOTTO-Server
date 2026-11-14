package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import com.github.nexters.ppotto.sticker.application.port.singlePort
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerViewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class StickerWithdrawalService(
    private val stickerRepository: StickerRepository,
    private val stickerCommandRepository: StickerCommandRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    private val stickerViewRepository: StickerViewRepository,
    private val imageStoragePorts: List<StickerImageStoragePort>,
    private val transactionTemplate: TransactionTemplate,
) {
    fun deleteAllByUserAndBoardIds(
        userId: UserId,
        boardIds: Collection<BoardId>,
    ) {
        val targets = stickerRepository.findDeletionTargetsByBoardIds(boardIds)
        if (targets.isNotEmpty()) {
            imageStoragePorts.singlePort("스티커 이미지 저장소").deleteAll(targets.mapNotNull { it.imageKey })
        }

        transactionTemplate.executeWithoutResult {
            stickerViewRepository.deleteAllByUser(userId)
            if (targets.isNotEmpty()) {
                val stickerIds = targets.map { it.id }
                stickerRecapRepository.deleteByStickerIds(stickerIds)
                stickerCommandRepository.hardDeleteByIds(stickerIds)
            }
        }
    }
}
