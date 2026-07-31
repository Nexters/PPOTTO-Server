package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardQueryService
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
    private val boardQueryService: BoardQueryService,
    private val drawingCommandPorts: List<StickerDrawingCommandPort>,
) {
    @Transactional
    fun rename(
        userId: UUID,
        stickerId: UUID,
        title: String,
    ): StickerTitleResult =
        getOwned(userId, stickerId)
            .also { it.rename(title) }
            .also {
                if (!stickerCommandRepository.updateTitle(it.id, it.title)) {
                    throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
                }
            }.let { StickerTitleResult(it.id, it.title) }

    @Transactional
    fun markViewed(
        userId: UUID,
        stickerId: UUID,
    ) {
        getOwned(userId, stickerId)
            .takeIf { it.viewedAt == null }
            ?.also { it.markViewed(Instant.now()) }
            ?.also {
                if (!stickerCommandRepository.markViewed(it.id, checkNotNull(it.viewedAt))) {
                    throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
                }
            }
    }

    @Transactional
    fun delete(
        userId: UUID,
        stickerId: UUID,
    ) {
        val drawingCommandPort = drawingCommandPort()
        getOwned(userId, stickerId)
            .also { it.delete(Instant.now()) }
            .also {
                if (!stickerCommandRepository.softDelete(it.id, checkNotNull(it.deletedAt))) {
                    throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
                }
            }.also { drawingCommandPort.deleteByStickerIds(it.boardId, listOf(it.id)) }
            .also { stickerRecapRepository.deleteByStickerIds(listOf(it.id)) }
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
        layouts
            .asSequence()
            .map { command ->
                stickersById
                    .getValue(command.id)
                    .also { it.updateLayout(command.toDomain()) }
            }.firstOrNull { !stickerCommandRepository.updateLayout(it) }
            ?.let {
                throw InvalidInputException(message = "편집할 수 없는 스티커가 포함되어 있습니다.")
            }
    }

    @Transactional
    fun deleteAllByBoardId(boardId: UUID) {
        stickerRepository
            .findAllByBoardId(boardId)
            .takeIf { it.isNotEmpty() }
            ?.let { stickers ->
                val drawingCommandPort = drawingCommandPort()
                val deletedAt = Instant.now()
                val stickerIds =
                    stickers
                        .asSequence()
                        .onEach { it.delete(deletedAt) }
                        .onEach {
                            if (!stickerCommandRepository.softDelete(it.id, deletedAt)) {
                                throw InvalidInputException(message = "삭제할 수 없는 스티커가 포함되어 있습니다.")
                            }
                        }.map(Sticker::id)
                        .toList()
                drawingCommandPort.deleteByStickerIds(boardId, stickerIds)
                stickerRecapRepository.deleteByStickerIds(stickerIds)
            }
    }

    private fun getOwned(
        userId: UUID,
        stickerId: UUID,
    ): Sticker =
        stickerRepository
            .findById(stickerId)
            ?.also { sticker ->
                boardQueryService
                    .getById(sticker.boardId)
                    .takeIf { it.userId == userId }
                    ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
            } ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)

    private fun drawingCommandPort(): StickerDrawingCommandPort =
        drawingCommandPorts.singleOrNull()
            ?: error("스티커 드로잉 삭제 application port 구현이 정확히 하나 필요합니다.")
}
