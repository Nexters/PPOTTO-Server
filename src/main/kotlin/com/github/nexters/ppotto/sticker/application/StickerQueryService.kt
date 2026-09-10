package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import com.github.nexters.ppotto.sticker.application.port.singlePort
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
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
    fun getByBoardId(boardId: BoardId): List<StickerItemResult> = toResults(stickerRepository.findAllByBoardId(boardId))

    fun getRecap(
        userId: UserId,
        stickerId: StickerId,
    ): StickerRecapResult = buildRecap(stickerAccessService.getOwned(userId, stickerId), isOwner = true, includePhotos = true)

    fun getSharedRecap(shareToken: String): StickerRecapResult {
        val sticker =
            stickerRepository.findByShareToken(shareToken)
                ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)

        return buildRecap(sticker, isOwner = false, includePhotos = sticker.sharePhotos)
    }

    private fun buildRecap(
        sticker: Sticker,
        isOwner: Boolean,
        includePhotos: Boolean,
    ): StickerRecapResult {
        val comments =
            stickerRecapRepository
                .findComments(sticker.id)
                .map { RecapCommentResult(it.id, it.content, it.posX, it.posY) }
        val photos =
            if (includePhotos) {
                stickerRecapRepository
                    .findPhotoIds(sticker.id)
                    .takeIf { it.isNotEmpty() }
                    ?.let { toPhotoResults(sticker, it) }
                    ?: emptyList()
            } else {
                emptyList()
            }

        return StickerRecapResult(
            sticker = toResults(listOf(sticker), isOwner).single(),
            summary = sticker.summary,
            share = if (isOwner && sticker.shareToken != null) RecapShareResult(sticker.sharePhotos) else null,
            comments = comments,
            photos = photos,
        )
    }

    private fun toPhotoResults(
        sticker: Sticker,
        photoIds: List<PhotoId>,
    ): List<RecapPhotoResult> {
        val recapPhotos =
            recapPhotoQueryPorts
                .singlePort("리캡 사진 조회")
                .getByIds(sticker.analysisId, sticker.boardId, photoIds)
        check(recapPhotos.map { it.id }.toSet() == photoIds.toSet()) {
            "리캡 사진 조회 결과가 요청한 사진과 일치하지 않습니다."
        }

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
    ): List<StickerItemResult> {
        val imageUrls =
            stickers
                .mapNotNull { it.imageKey }
                .toSet()
                .takeIf { it.isNotEmpty() }
                ?.let { stickerImageStoragePorts.singlePort("스티커 이미지 저장소").issueReadUrls(it) }
                ?: emptyMap()

        return stickers.map { sticker ->
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
}
