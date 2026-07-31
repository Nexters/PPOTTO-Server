package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
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
    private val stickerImageStoragePorts: List<StickerImageStoragePort>,
) {
    fun getByBoardId(boardId: UUID): List<StickerItemResult> = stickerRepository.findAllByBoardId(boardId).let(::toResults)

    fun getRecap(
        userId: UUID,
        stickerId: UUID,
    ): StickerRecapResult =
        getOwned(userId, stickerId).let { sticker ->
            stickerRecapRepository
                .findComments(stickerId)
                .map { RecapCommentResult(it.id, it.content, it.isFloat, it.posX, it.posY) }
                .let { comments ->
                    stickerRecapRepository
                        .findPhotoIds(stickerId)
                        .takeIf { it.isNotEmpty() }
                        ?.let { photoIds ->
                            photoQueryPort()
                                .getByIds(photoIds)
                                .also { checkPhotoContract(photoIds, it) }
                                .sortedWith(compareBy<RecapPhotoMetadata> { it.takenAt }.thenBy { it.id })
                                .map { RecapPhotoResult(it.id, it.imageUrl, it.takenAt) }
                        }.orEmpty()
                        .let { StickerRecapResult(toResults(listOf(sticker)).single(), comments, it) }
                }
        }

    private fun getOwned(
        userId: UUID,
        stickerId: UUID,
    ): Sticker =
        (stickerRepository.findById(stickerId) ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND))
            .also { sticker ->
                boardAccessService
                    .getById(sticker.boardId)
                    .takeIf { it.userId == userId }
                    ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
            }

    private fun toResults(stickers: List<Sticker>): List<StickerItemResult> =
        stickers
            .mapNotNull { it.imageKey }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { imageStoragePort().issueReadUrls(it) }
            .orEmpty()
            .let { imageUrls ->
                stickers.map { sticker ->
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

    private fun imageStoragePort(): StickerImageStoragePort =
        stickerImageStoragePorts.singleOrNull() ?: error("스티커 이미지 저장소 application port 구현이 필요합니다.")

    private fun checkPhotoContract(
        requestedIds: Collection<UUID>,
        photos: List<RecapPhotoMetadata>,
    ): Unit =
        check(photos.map { it.id }.toSet() == requestedIds.toSet()) {
            "리캡 사진 조회 결과가 요청한 사진과 일치하지 않습니다."
        }
}
