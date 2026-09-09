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
import com.github.nexters.ppotto.sticker.application.port.StickerRegenerationResult
import com.github.nexters.ppotto.sticker.application.port.singlePort
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.domain.StickerImageDeletionReason
import com.github.nexters.ppotto.sticker.domain.StickerImageDeletionRequestedEvent
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
    ): StickerTitleResult {
        val sticker = stickerAccessService.getOwned(userId, stickerId)
        sticker.rename(title)

        if (!stickerCommandRepository.updateTitle(sticker.id, sticker.title)) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
        return StickerTitleResult(sticker.id, sticker.title)
    }

    @Transactional
    fun markViewed(
        userId: UserId,
        stickerId: StickerId,
    ) {
        val sticker = stickerAccessService.getOwned(userId, stickerId)
        if (sticker.viewedAt != null) {
            return
        }

        sticker.markViewed(Instant.now())
        if (!stickerCommandRepository.markViewed(sticker.id, checkNotNull(sticker.viewedAt))) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
    }

    @Transactional
    fun delete(
        userId: UserId,
        stickerId: StickerId,
    ) {
        val sticker = stickerAccessService.getOwned(userId, stickerId)
        val drawingCommandPort = drawingCommandPorts.singlePort(DRAWING_COMMAND_PORT_NAME)
        sticker.delete(Instant.now())

        if (!stickerCommandRepository.softDelete(sticker.id, checkNotNull(sticker.deletedAt))) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
        drawingCommandPort.deleteByStickerIds(sticker.boardId, listOf(sticker.id))
        stickerRecapRepository.deleteByStickerIds(listOf(sticker.id))
    }

    fun regenerate(
        userId: UserId,
        stickerId: StickerId,
    ) {
        val sticker = stickerAccessService.getOwned(userId, stickerId)
        val photoIds = validateRegeneratable(sticker)
        val previousSourcePhotoId = checkNotNull(sticker.sourcePhotoId) { "이미지형 스티커의 소스 사진이 비어 있습니다." }
        val previousImageKey = sticker.imageKey

        val now = Instant.now()
        if (!stickerCommandRepository.tryClaimRegenerationLock(stickerId, now, now.plus(REGENERATION_LOCK_TTL))) {
            throw ConflictException(StickerErrorCode.STICKER_REGENERATION_IN_PROGRESS)
        }

        try {
            val result =
                stickerRegenerationPorts.singlePort("스티커 재생성").regenerate(
                    analysisId = sticker.analysisId,
                    boardId = sticker.boardId,
                    stickerId = sticker.id,
                    photoIds = photoIds,
                    previousSourcePhotoId = previousSourcePhotoId,
                ) ?: throw InvalidInputException(StickerErrorCode.REGENERATION_PHOTOS_NOT_FOUND)
            swapStickerImage(sticker, result)

            if (previousImageKey != null && previousImageKey != result.imageKey) {
                publishStickerImageDeletion(
                    stickerId = stickerId,
                    imageKeys = listOf(previousImageKey),
                    reason = StickerImageDeletionReason.REGENERATED_IMAGE_REPLACED,
                )
            }
        } finally {
            stickerCommandRepository.releaseRegenerationLock(stickerId)
        }
    }

    private fun swapStickerImage(
        sticker: Sticker,
        result: StickerRegenerationResult,
    ) {
        var swapped = false
        try {
            transactionTemplate.executeWithoutResult {
                sticker.regenerateSticker(result.sourcePhotoId, result.imageKey, result.mainColor)

                if (!stickerCommandRepository.updateStickerImage(sticker)) {
                    throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
                }
            }
            swapped = true
        } finally {
            if (!swapped) {
                publishStickerImageDeletion(
                    stickerId = sticker.id,
                    imageKeys = listOf(result.imageKey),
                    reason = StickerImageDeletionReason.REGENERATED_IMAGE_DB_UPDATE_FAILED,
                )
            }
        }
    }

    private fun validateRegeneratable(sticker: Sticker): List<PhotoId> {
        if (sticker.type != StickerType.IMAGE) {
            throw InvalidInputException(StickerErrorCode.NOT_REGENERATABLE_STICKER_TYPE)
        }
        return stickerRecapRepository
            .findPhotoIds(sticker.id)
            .ifEmpty { throw InvalidInputException(StickerErrorCode.REGENERATION_PHOTOS_NOT_FOUND) }
    }

    fun validateOwnedByBoard(
        boardId: BoardId,
        stickerIds: Collection<StickerId>,
    ): Boolean = stickerRepository.validateOwnedByBoard(boardId, stickerIds)

    @Transactional
    fun updateLayouts(
        boardId: BoardId,
        layouts: List<StickerLayoutCommand>,
    ) {
        val ids = layouts.map { it.id }
        val stickersById = stickerRepository.findAllByBoardId(boardId).associateBy { it.id }
        val editable = ids.distinct().size == ids.size && stickersById.keys.containsAll(ids)
        if (!editable) {
            throw InvalidInputException(StickerErrorCode.UNEDITABLE_STICKER)
        }

        for (command in layouts) {
            val sticker = stickersById.getValue(command.id)
            sticker.updateLayout(command.layout)
            if (!stickerCommandRepository.updateLayout(sticker)) {
                throw InvalidInputException(StickerErrorCode.UNEDITABLE_STICKER)
            }
        }
    }

    @Transactional
    fun deleteAllByBoardId(boardId: BoardId) {
        val stickers = stickerRepository.findAllByBoardId(boardId)
        if (stickers.isEmpty()) {
            return
        }

        val drawingCommandPort = drawingCommandPorts.singlePort(DRAWING_COMMAND_PORT_NAME)
        val deletedAt = Instant.now()
        stickers.forEach { sticker ->
            sticker.delete(deletedAt)
            if (!stickerCommandRepository.softDelete(sticker.id, deletedAt)) {
                throw InvalidInputException(StickerErrorCode.UNDELETABLE_STICKER)
            }
        }

        val stickerIds = stickers.map { it.id }
        drawingCommandPort.deleteByStickerIds(boardId, stickerIds)
        stickerRecapRepository.deleteByStickerIds(stickerIds)
    }

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
        private const val DRAWING_COMMAND_PORT_NAME = "스티커 드로잉 삭제"
        private val REGENERATION_LOCK_TTL: Duration = Duration.ofMinutes(5)
    }
}
