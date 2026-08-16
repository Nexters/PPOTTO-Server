package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import com.github.nexters.ppotto.sticker.application.port.StickerRegenerationPort
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

@Service
class StickerCommandService(
    private val stickerRepository: StickerRepository,
    private val stickerCommandRepository: StickerCommandRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    private val stickerAccessService: StickerAccessService,
    private val drawingCommandPorts: List<StickerDrawingCommandPort>,
    private val stickerRegenerationPorts: List<StickerRegenerationPort>,
    private val transactionTemplate: TransactionTemplate,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun rename(
        userId: UserId,
        stickerId: StickerId,
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
        userId: UserId,
        stickerId: StickerId,
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
        userId: UserId,
        stickerId: StickerId,
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

    fun regenerate(
        userId: UserId,
        stickerId: StickerId,
    ): Sticker {
        val sticker = stickerAccessService.getOwned(userId, stickerId)
        val photoIds = validateRegeneratable(sticker, stickerId)
        val previousSourcePhotoId = checkNotNull(sticker.sourcePhotoId) { "이미지형 스티커의 소스 사진이 비어 있습니다." }
        val previousImageKey = sticker.imageKey

        val now = Instant.now()
        if (!stickerCommandRepository.tryClaimRegenerationLock(stickerId, now, now.plus(REGENERATION_LOCK_TTL))) {
            throw ConflictException(StickerErrorCode.STICKER_REGENERATION_IN_PROGRESS)
        }

        try {
            val regenerationPort =
                stickerRegenerationPorts.singleOrNull()
                    ?: error("스티커 재생성 application port 구현이 정확히 하나 필요합니다.")
            val result =
                regenerationPort.regenerate(
                    analysisId = sticker.analysisId,
                    boardId = sticker.boardId,
                    stickerId = sticker.id,
                    photoIds = photoIds,
                    previousSourcePhotoId = previousSourcePhotoId,
                )

            runCatching {
                transactionTemplate.execute {
                    sticker.regenerateSticker(result.sourcePhotoId, result.imageKey, result.mainColor)

                    if (!stickerCommandRepository.updateStickerImage(sticker)) {
                        throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
                    }
                }
            }.onFailure {
                publishStickerImageDeletion(
                    stickerId = stickerId,
                    imageKeys = listOf(result.imageKey),
                    reason = StickerImageDeletionReason.REGENERATED_IMAGE_DB_UPDATE_FAILED,
                )
            }.getOrThrow()

            if (previousImageKey != null && previousImageKey != result.imageKey) {
                publishStickerImageDeletion(
                    stickerId = stickerId,
                    imageKeys = listOf(previousImageKey),
                    reason = StickerImageDeletionReason.REGENERATED_IMAGE_REPLACED,
                )
            }

            return sticker
        } finally {
            stickerCommandRepository.releaseRegenerationLock(stickerId)
        }
    }

    private fun validateRegeneratable(
        sticker: Sticker,
        stickerId: StickerId,
    ): List<PhotoId> {
        if (sticker.type != StickerType.IMAGE) {
            throw InvalidInputException(message = "이미지형 스티커만 재생성할 수 있습니다.")
        }
        return stickerRecapRepository
            .findPhotoIds(stickerId)
            .ifEmpty { throw InvalidInputException(message = "재생성할 사진 구성이 없습니다.") }
    }

    fun validateOwnedByBoard(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    ): Boolean = stickerRepository.validateOwnedByBoard(boardId, stickerIds)

    @Transactional
    fun updateLayouts(
        boardId: BoardId,
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
    fun deleteAllByBoardId(boardId: BoardId): Unit =
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

    private fun publishStickerImageDeletion(
        stickerId: StickerId,
        imageKeys: List<String>,
        reason: StickerImageDeletionReason,
    ) {
        eventPublisher.publishEvent(
            StickerImageDeletionRequestedEvent(
                stickerId = stickerId,
                imageKeys = imageKeys,
                reason = reason,
            ),
        )
    }

    companion object {
        private val REGENERATION_LOCK_TTL: Duration = Duration.ofMinutes(5)
    }
}
