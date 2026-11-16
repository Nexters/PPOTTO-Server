package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.application.AnalysisQueryService
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.application.model.RecapCommentResult
import com.github.nexters.ppotto.sticker.application.model.RecapGroupPhotoResult
import com.github.nexters.ppotto.sticker.application.model.RecapPhotoResult
import com.github.nexters.ppotto.sticker.application.model.RecapShareResult
import com.github.nexters.ppotto.sticker.application.model.StickerBoardSummaryResult
import com.github.nexters.ppotto.sticker.application.model.StickerItemResult
import com.github.nexters.ppotto.sticker.application.model.StickerRecapResult
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import com.github.nexters.ppotto.sticker.application.port.singlePort
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerViewRepository
import com.github.nexters.ppotto.user.application.UserService
import org.springframework.stereotype.Service

@Service
class StickerQueryService(
    private val stickerRepository: StickerRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    private val stickerViewRepository: StickerViewRepository,
    private val stickerAccessService: StickerAccessService,
    private val analysisQueryService: AnalysisQueryService,
    private val userService: UserService,
    private val recapPhotoQueryPorts: List<RecapPhotoQueryPort>,
    private val stickerImageStoragePorts: List<StickerImageStoragePort>,
) {
    fun getByBoardId(
        boardId: BoardId,
        userId: UserId?,
    ): List<StickerItemResult> = toResults(stickerRepository.findAllByBoardId(boardId), userId)

    fun getBoardSummaries(
        boardIds: Set<BoardId>,
        previewImageCount: Int,
    ): Map<BoardId, StickerBoardSummaryResult> {
        val previewsByBoardId =
            stickerRepository
                .findRecentImagesByBoardIds(boardIds, previewImageCount)
                .groupBy { it.boardId }
        val imageUrls =
            issueReadUrls(
                previewsByBoardId.values
                    .flatten()
                    .map { it.imageKey },
            )

        return stickerRepository.findStatsByBoardIds(boardIds).associate { stats ->
            val previews = previewsByBoardId[stats.boardId].orEmpty()
            stats.boardId to
                StickerBoardSummaryResult(
                    stickerCount = stats.stickerCount,
                    previewImageUrls = previews.map { imageUrls[it.imageKey] ?: error("스티커 이미지 읽기 URL이 누락되었습니다.") },
                    mainColor = previews.firstOrNull()?.mainColor,
                    lastAddedAt = stats.lastAddedAt,
                )
        }
    }

    fun getRecap(
        userId: UserId,
        stickerId: StickerId,
    ): StickerRecapResult = buildRecap(stickerAccessService.getAccessible(userId, stickerId), userId, includePhotos = true)

    fun getSharedRecap(shareToken: String): StickerRecapResult {
        val sticker =
            stickerRepository.findByShareToken(shareToken)
                ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)

        return buildRecap(sticker, null, includePhotos = sticker.sharePhotos)
    }

    fun findRecapInBoard(
        boardId: BoardId,
        stickerId: StickerId,
        includePhotos: Boolean,
    ): StickerRecapResult? =
        stickerRepository
            .findById(stickerId)
            ?.takeIf { it.boardId == boardId }
            ?.let { buildRecap(it, null, includePhotos = includePhotos) }

    private fun buildRecap(
        sticker: Sticker,
        userId: UserId?,
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
            sticker = toResults(listOf(sticker), userId).single(),
            summary = sticker.summary,
            share = if (userId != null && sticker.shareToken != null) RecapShareResult(sticker.sharePhotos) else null,
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

    private fun issueReadUrls(imageKeys: Collection<String>): Map<String, String> =
        imageKeys
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { stickerImageStoragePorts.singlePort("스티커 이미지 저장소").issueReadUrls(it) }
            ?: emptyMap()

    private fun toResults(
        stickers: List<Sticker>,
        userId: UserId?,
    ): List<StickerItemResult> {
        if (stickers.isEmpty()) return emptyList()
        val creatorIds = analysisQueryService.findCreatorIds(stickers.map { it.analysisId }.toSet())
        val creatorNames = userService.findDisplayNames(creatorIds.values.toSet())
        val imageUrls = issueReadUrls(stickers.mapNotNull { it.imageKey })
        val viewedStickerIds =
            userId?.let { stickerViewRepository.findViewedStickerIds(it, stickers.map { sticker -> sticker.id }) }
                ?: emptySet()

        return stickers.map { sticker ->
            val creatorId = checkNotNull(creatorIds[sticker.analysisId]) { "스티커 분석의 작성자가 누락되었습니다: ${sticker.id}" }
            StickerItemResult(
                id = sticker.id,
                title = sticker.title,
                creatorName = checkNotNull(creatorNames[creatorId]) { "스티커 작성자 이름이 누락되었습니다: $creatorId" },
                isNew = userId != null && sticker.id !in viewedStickerIds,
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
