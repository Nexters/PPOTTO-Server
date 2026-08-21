package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import org.springframework.stereotype.Service

@Service
class StickerQueryService(
    private val stickerRepository: StickerRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    private val stickerAccessService: StickerAccessService,
    private val recapPhotoQueryPorts: List<RecapPhotoQueryPort>,
    private val stickerImageStoragePorts: List<StickerImageStoragePort>,
) {
    fun getByBoardId(boardId: BoardId): List<StickerItemResult> = stickerRepository.findAllByBoardId(boardId).let(::toResults)

    fun getRecap(
        userId: UserId?,
        stickerId: StickerId,
    ): StickerRecapResult =
        stickerAccessService.getWithOwnership(userId, stickerId).let { (sticker, isOwner) ->
            stickerRecapRepository
                .findComments(stickerId)
                .map { RecapCommentResult(it.id, it.content, it.posX, it.posY) }
                .let { comments ->
                    val photos =
                        stickerRecapRepository
                            .findPhotoIds(stickerId)
                            .takeIf { it.isNotEmpty() }
                            ?.let { photoIds -> toPhotoResults(sticker, photoIds) }
                            .orEmpty()
                    StickerRecapResult(toResults(listOf(sticker), isOwner).single(), sticker.summary, comments, photos)
                }
        }

    private fun toPhotoResults(
        sticker: Sticker,
        photoIds: List<PhotoId>,
    ): List<RecapPhotoResult> {
        val recapPhotos =
            photoQueryPort()
                .getByIds(sticker.analysisId, sticker.boardId, photoIds)
                .also { checkPhotoContract(photoIds, it) }

        val groupPhotosByGroupId =
            recapPhotos
                .filterNot { it.isRepresentative }
                .filter { it.burstGroupId != null }
                .sortedWith(compareBy<RecapPhotoMetadata> { it.takenAt }.thenBy { it.id.value })
                .groupBy { it.burstGroupId }

        return recapPhotos
            .filter { it.isRepresentative }
            .sortedWith(compareBy<RecapPhotoMetadata> { it.takenAt }.thenBy { it.id.value })
            .map { photo ->
                RecapPhotoResult(
                    id = photo.id,
                    imageUrl = photo.imageUrl,
                    takenAt = photo.takenAt,
                    isGroup = photo.burstGroupId != null,
                    groupId = photo.burstGroupId,
                    groupPhotos =
                        groupPhotosByGroupId[photo.burstGroupId]
                            ?.map { RecapGroupPhotoResult(it.id, it.imageUrl, it.takenAt) }
                            .orEmpty(),
                )
            }
    }

    private fun toResults(
        stickers: List<Sticker>,
        isOwner: Boolean = true,
    ): List<StickerItemResult> =
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
                        isNew = isOwner && sticker.viewedAt == null,
                        type = sticker.type,
                        imageUrl = sticker.imageKey?.let { imageUrls[it] ?: error("스티커 이미지 읽기 URL이 누락되었습니다.") },
                        textContent = sticker.textContent,
                        mainColor = sticker.mainColor,
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
        requestedIds: Collection<PhotoId>,
        photos: List<RecapPhotoMetadata>,
    ): Unit =
        check(photos.map { it.id }.toSet() == requestedIds.toSet()) {
            "리캡 사진 조회 결과가 요청한 사진과 일치하지 않습니다."
        }
}
