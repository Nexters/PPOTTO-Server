package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.application.port.StickerImageQueryPort
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StickerQueryService(
    private val stickerRepository: StickerRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    private val boardAccessService: BoardAccessService,
    private val recapPhotoQueryPorts: List<RecapPhotoQueryPort>,
    private val stickerImageQueryPorts: List<StickerImageQueryPort>,
) {
    fun getByBoardId(boardId: UUID): List<StickerItemResult> {
        val stickers = stickerRepository.findAllByBoardId(boardId)
        return toResults(stickers)
    }

    fun getRecap(
        userId: UUID,
        stickerId: UUID,
    ): StickerRecapResult {
        val sticker = getOwned(userId, stickerId)
        val comments =
            stickerRecapRepository
                .findComments(stickerId)
                .map { RecapCommentResult(it.id, it.content, it.isFloat, it.posX, it.posY) }
        val photoIds = stickerRecapRepository.findPhotoIds(stickerId)
        val photos =
            if (photoIds.isEmpty()) {
                emptyList()
            } else {
                photoQueryPort()
                    .getByIds(photoIds)
                    .also { checkPhotoContract(photoIds, it) }
                    .sortedWith(compareBy<RecapPhotoMetadata> { it.takenAt }.thenBy { it.id })
                    .map { RecapPhotoResult(it.id, it.imageUrl, it.takenAt) }
            }
        return StickerRecapResult(toResults(listOf(sticker)).single(), comments, photos)
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

    private fun toResults(stickers: List<Sticker>): List<StickerItemResult> {
        val imageKeys = stickers.mapNotNull { it.imageKey }.toSet()
        val imageUrls =
            if (imageKeys.isEmpty()) {
                emptyMap()
            } else {
                imageQueryPort().issueReadUrls(imageKeys)
            }
        return stickers.map { sticker ->
            StickerItemResult(
                id = sticker.id,
                title = sticker.title,
                isNew = sticker.viewedAt == null,
                type = sticker.type,
                imageUrl = sticker.imageKey?.let { imageUrls[it] ?: error("스티커 이미지 읽기 URL이 누락되었습니다.") },
                textContent = sticker.textContent,
                posX = sticker.posX,
                posY = sticker.posY,
                scale = sticker.scale,
                rotation = sticker.rotation,
                zIndex = sticker.zIndex,
                badgeOffsetX = sticker.badgeOffsetX,
                badgeOffsetY = sticker.badgeOffsetY,
                badgeRotation = sticker.badgeRotation,
            )
        }
    }

    private fun photoQueryPort(): RecapPhotoQueryPort = recapPhotoQueryPorts.singleOrNull() ?: error("리캡 사진 조회 application port 구현이 필요합니다.")

    private fun imageQueryPort(): StickerImageQueryPort =
        stickerImageQueryPorts.singleOrNull() ?: error("스티커 이미지 조회 application port 구현이 필요합니다.")

    private fun checkPhotoContract(
        requestedIds: Collection<UUID>,
        photos: List<RecapPhotoMetadata>,
    ) {
        check(photos.map { it.id }.toSet() == requestedIds.toSet()) {
            "리캡 사진 조회 결과가 요청한 사진과 일치하지 않습니다."
        }
    }
}
