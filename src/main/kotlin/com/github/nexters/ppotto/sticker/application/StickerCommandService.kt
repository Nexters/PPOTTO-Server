package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class StickerCommandService(
    private val stickerRepository: StickerRepository,
    private val stickerCommandRepository: StickerCommandRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    private val boardAccessService: BoardAccessService,
    private val drawingCommandPorts: List<StickerDrawingCommandPort>,
) {
    @Transactional
    fun rename(
        userId: UUID,
        stickerId: UUID,
        title: String,
    ): StickerTitleResult {
        val sticker = getOwned(userId, stickerId)
        sticker.rename(title)
        if (!stickerCommandRepository.updateTitle(sticker.id, sticker.title)) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
        return StickerTitleResult(sticker.id, sticker.title)
    }

    @Transactional
    fun markViewed(
        userId: UUID,
        stickerId: UUID,
    ) {
        val sticker = getOwned(userId, stickerId)
        if (sticker.viewedAt == null) {
            sticker.markViewed(Instant.now())
            if (!stickerCommandRepository.markViewed(sticker.id, checkNotNull(sticker.viewedAt))) {
                throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
            }
        }
    }

    @Transactional
    fun delete(
        userId: UUID,
        stickerId: UUID,
    ) {
        val sticker = getOwned(userId, stickerId)
        val drawingCommandPort = drawingCommandPort()
        sticker.delete(Instant.now())
        if (!stickerCommandRepository.softDelete(sticker.id, checkNotNull(sticker.deletedAt))) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
        drawingCommandPort.deleteByStickerIds(sticker.boardId, listOf(stickerId))
        stickerRecapRepository.deleteByStickerIds(listOf(stickerId))
    }

    fun validateOwnedByBoard(
        boardId: UUID,
        stickerIds: Collection<UUID>,
    ): Boolean = stickerRepository.validateOwnedByBoard(boardId, stickerIds)

    @Transactional
    fun updateLayouts(
        boardId: UUID,
        layouts: List<StickerLayoutCommand>,
    ) {
        val ids = layouts.map { it.id }
        if (ids.distinct().size != ids.size || !validateOwnedByBoard(boardId, ids)) {
            throw InvalidInputException(message = "편집할 수 없는 스티커가 포함되어 있습니다.")
        }
        val stickersById = stickerRepository.findAllByBoardId(boardId).associateBy { it.id }
        layouts.forEach { command ->
            val sticker = stickersById.getValue(command.id)
            sticker.updateLayout(command.toDomain())
            if (!stickerCommandRepository.updateLayout(sticker)) {
                throw InvalidInputException(message = "편집할 수 없는 스티커가 포함되어 있습니다.")
            }
        }
    }

    @Transactional
    fun deleteAllByBoardId(boardId: UUID) {
        val stickers = stickerRepository.findAllByBoardId(boardId)
        if (stickers.isEmpty()) return
        val drawingCommandPort = drawingCommandPort()
        val deletedAt = Instant.now()
        stickers.forEach { sticker ->
            sticker.delete(deletedAt)
            if (!stickerCommandRepository.softDelete(sticker.id, deletedAt)) {
                throw InvalidInputException(message = "삭제할 수 없는 스티커가 포함되어 있습니다.")
            }
        }
        val stickerIds = stickers.map { it.id }
        drawingCommandPort.deleteByStickerIds(boardId, stickerIds)
        stickerRecapRepository.deleteByStickerIds(stickerIds)
    }

    private fun getOwned(
        userId: UUID,
        stickerId: UUID,
    ): Sticker {
        val sticker = stickerRepository.findById(stickerId) ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        val board = boardAccessService.getById(sticker.boardId)
        if (board.userId != userId) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
        return sticker
    }

    private fun drawingCommandPort(): StickerDrawingCommandPort =
        drawingCommandPorts.singleOrNull()
            ?: error("스티커 드로잉 삭제 application port 구현이 정확히 하나 필요합니다.")
}
