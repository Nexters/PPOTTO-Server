package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
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
    private val stickerAccessService: StickerAccessService,
    private val drawingCommandPorts: List<StickerDrawingCommandPort>,
) {
    @Transactional
    fun rename(
        userId: UUID,
        stickerId: UUID,
        title: String,
    ): StickerTitleResult =
        stickerAccessService
            .getOwned(userId, stickerId)
            .apply { rename(title) }
            .takeIf { stickerCommandRepository.updateTitle(it.id, it.title) }
            ?.let { StickerTitleResult(it.id, it.title) }
            ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)

    @Transactional
    fun markViewed(
        userId: UUID,
        stickerId: UUID,
    ) {
        stickerAccessService
            .getOwned(userId, stickerId)
            .takeIf { it.viewedAt == null }
            ?.apply { markViewed(Instant.now()) }
            ?.let {
                it
                    .takeIf { sticker -> stickerCommandRepository.markViewed(sticker.id, checkNotNull(sticker.viewedAt)) }
                    ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
            }
    }

    @Transactional
    fun delete(
        userId: UUID,
        stickerId: UUID,
    ): Unit =
        stickerAccessService.getOwned(userId, stickerId).let { sticker ->
            drawingCommandPort().let { drawingCommandPort ->
                sticker
                    .apply { delete(Instant.now()) }
                    .takeIf { stickerCommandRepository.softDelete(it.id, checkNotNull(it.deletedAt)) }
                    ?.also {
                        drawingCommandPort.deleteByStickerIds(it.boardId, listOf(it.id))
                    }.let {
                        it ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
                    }.let {
                        stickerRecapRepository.deleteByStickerIds(listOf(it.id))
                    }
            }
        }

    fun validateOwnedByBoard(
        boardId: UUID,
        stickerIds: Collection<UUID>,
    ): Boolean = stickerRepository.validateOwnedByBoard(boardId, stickerIds)

    @Transactional
    fun updateLayouts(
        boardId: UUID,
        layouts: List<StickerLayoutCommand>,
    ): Unit =
        (
            layouts
                .map { it.id }
                .takeIf { it.distinct().size == it.size }
                ?: throw uneditableSticker()
        ).let { stickerRepository.findAllByBoardId(boardId).associateBy { sticker -> sticker.id } }
            .let { stickersById ->
                layouts.forEach { command ->
                    (stickersById[command.id] ?: throw uneditableSticker())
                        .apply { updateLayout(command.toDomain()) }
                        .takeIf(stickerCommandRepository::updateLayout)
                        ?: throw uneditableSticker()
                }
            }

    @Transactional
    fun deleteAllByBoardId(boardId: UUID): Unit =
        stickerRepository
            .findAllByBoardId(boardId)
            .takeIf { it.isNotEmpty() }
            ?.let { stickers ->
                drawingCommandPort().let { drawingCommandPort ->
                    Instant.now().let { deletedAt ->
                        stickers
                            .onEach {
                                it
                                    .apply { delete(deletedAt) }
                                    .takeIf { sticker -> stickerCommandRepository.softDelete(sticker.id, deletedAt) }
                                    ?: throw InvalidInputException(message = "삭제할 수 없는 스티커가 포함되어 있습니다.")
                            }.map { it.id }
                            .also { drawingCommandPort.deleteByStickerIds(boardId, it) }
                            .let(stickerRecapRepository::deleteByStickerIds)
                    }
                }
            } ?: Unit

    private fun uneditableSticker() = InvalidInputException(message = "편집할 수 없는 스티커가 포함되어 있습니다.")

    private fun drawingCommandPort(): StickerDrawingCommandPort =
        drawingCommandPorts.singleOrNull()
            ?: error("스티커 드로잉 삭제 application port 구현이 정확히 하나 필요합니다.")
}
