package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipPort
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipScope
import com.github.nexters.ppotto.sticker.domain.StickerCreation
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AnalysisResultSaveService(
    private val stickerRepository: StickerRepository,
    private val stickerRecapRepository: StickerRecapRepository,
    private val boardQueryService: BoardQueryService,
    private val ownershipPorts: List<AnalysisPhotoOwnershipPort>,
) {
    @Transactional
    fun save(command: SaveAnalysisResultCommand): SavedAnalysisResult {
        validateStickerCount(command.stickers.size)
        ownershipPort().let { validateOwnership(command, it) }
        stickerRepository.lockAnalysisResult(command.analysisId)
        return stickerRepository
            .findAllByAnalysisId(command.analysisId)
            .takeIf { it.isNotEmpty() }
            ?.map { it.id }
            ?.let(::SavedAnalysisResult)
            ?: command.stickers
                .map { saveSticker(command, it) }
                .let(::SavedAnalysisResult)
    }

    private fun saveSticker(
        command: SaveAnalysisResultCommand,
        result: AnalysisStickerResult,
    ) = stickerRepository
        .save(command.analysisId, command.boardId, result.toCreation())
        .also { stickerRecapRepository.savePhotos(it.id, result.photoIds) }
        .also { stickerRecapRepository.saveComments(it.id, result.comments) }
        .id

    private fun AnalysisStickerResult.toCreation(): StickerCreation =
        StickerCreation(
            type = type,
            title = title,
            sourcePhotoId = sourcePhotoId,
            imageKey = imageKey,
            textContent = textContent,
            layout = layout,
        )

    private fun validateStickerCount(stickerCount: Int) {
        if (stickerCount > MAX_STICKER_COUNT) {
            throw InvalidInputException(message = "분석 결과 스티커는 최대 6개까지 저장할 수 있습니다.")
        }
    }

    private fun ownershipPort(): AnalysisPhotoOwnershipPort =
        ownershipPorts.singleOrNull()
            ?: error("분석과 사진 소유권 검증 application port 구현이 정확히 하나 필요합니다.")

    private fun validateOwnership(
        command: SaveAnalysisResultCommand,
        ownershipPort: AnalysisPhotoOwnershipPort,
    ) {
        boardQueryService
            .getById(command.boardId)
            .takeIf { it.userId == command.userId }
            ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        val photoIds =
            command.stickers
                .flatMap { result -> result.photoIds + listOfNotNull(result.sourcePhotoId) }
                .toSet()
        ownershipPort
            .matches(
                AnalysisPhotoOwnershipScope(
                    userId = command.userId,
                    boardId = command.boardId,
                    analysisId = command.analysisId,
                    photoIds = photoIds,
                ),
            ).takeIf { it }
            ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
    }

    companion object {
        const val MAX_STICKER_COUNT = 6
    }
}
