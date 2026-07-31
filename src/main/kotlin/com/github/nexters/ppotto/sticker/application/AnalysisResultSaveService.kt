package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardAccessService
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
    private val boardAccessService: BoardAccessService,
    private val ownershipPorts: List<AnalysisPhotoOwnershipPort>,
) {
    @Transactional
    fun save(command: SaveAnalysisResultCommand): SavedAnalysisResult =
        command
            .also {
                validateStickerCount(it.stickers.size)
                validateOwnership(it, ownershipPort())
                stickerRepository.lockAnalysisResult(it.analysisId)
            }.let { command ->
                stickerRepository
                    .findAllByAnalysisId(command.analysisId)
                    .takeIf { stickers -> stickers.isNotEmpty() }
                    ?.map { sticker -> sticker.id }
                    ?.let(::SavedAnalysisResult)
                    ?: command.stickers
                        .map { result ->
                            StickerCreation(
                                type = result.type,
                                title = result.title,
                                summary = result.summary,
                                sourcePhotoId = result.sourcePhotoId,
                                imageKey = result.imageKey,
                                textContent = result.textContent,
                                layout = result.layout,
                            ).let { creation ->
                                stickerRepository.save(command.analysisId, command.boardId, creation)
                            }.also { sticker ->
                                stickerRecapRepository
                                    .savePhotos(sticker.id, result.photoIds)
                                    .let { stickerRecapRepository.saveComments(sticker.id, result.comments) }
                            }.id
                        }.let(::SavedAnalysisResult)
            }

    private fun validateStickerCount(stickerCount: Int) {
        stickerCount
            .takeIf { it <= MAX_STICKER_COUNT }
            ?: throw InvalidInputException(message = "분석 결과 스티커는 최대 6개까지 저장할 수 있습니다.")
    }

    private fun ownershipPort(): AnalysisPhotoOwnershipPort =
        ownershipPorts.singleOrNull()
            ?: error("분석과 사진 소유권 검증 application port 구현이 정확히 하나 필요합니다.")

    private fun validateOwnership(
        command: SaveAnalysisResultCommand,
        ownershipPort: AnalysisPhotoOwnershipPort,
    ) {
        command
            .also {
                boardAccessService
                    .getById(it.boardId)
                    .takeIf { board -> board.userId == it.userId }
                    ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
            }.let {
                it.stickers
                    .flatMap { sticker -> sticker.photoIds + listOfNotNull(sticker.sourcePhotoId) }
                    .toSet()
            }.let {
                AnalysisPhotoOwnershipScope(
                    userId = command.userId,
                    boardId = command.boardId,
                    analysisId = command.analysisId,
                    photoIds = it,
                )
            }.takeIf(ownershipPort::matches)
            ?: throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
    }

    companion object {
        const val MAX_STICKER_COUNT = 6
    }
}
