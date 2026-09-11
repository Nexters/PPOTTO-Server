package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.lock.AdvisoryLock
import com.github.nexters.ppotto.sticker.application.model.SaveAnalysisResultCommand
import com.github.nexters.ppotto.sticker.application.model.SavedAnalysisResult
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipPort
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipScope
import com.github.nexters.ppotto.sticker.application.port.singlePort
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
    @AdvisoryLock(namespace = "analysis-result", key = "#command.analysisId")
    fun save(command: SaveAnalysisResultCommand): SavedAnalysisResult {
        validateOwnership(command)

        val existingStickerIds = stickerRepository.findAllByAnalysisId(command.analysisId).map { it.id }
        if (existingStickerIds.isNotEmpty()) {
            return SavedAnalysisResult(existingStickerIds)
        }

        val stickerIds =
            command.stickers.map { result ->
                val creation =
                    StickerCreation(
                        type = result.type,
                        title = result.title,
                        summary = result.summary,
                        sourcePhotoId = result.sourcePhotoId,
                        imageKey = result.imageKey,
                        textContent = result.textContent,
                        mainColor = result.mainColor,
                    )
                val sticker = stickerRepository.save(command.analysisId, command.boardId, creation)
                stickerRecapRepository.savePhotos(sticker.id, result.photoIds)
                stickerRecapRepository.saveComments(sticker.id, result.comments)
                sticker.id
            }
        return SavedAnalysisResult(stickerIds)
    }

    private fun validateOwnership(command: SaveAnalysisResultCommand) {
        val ownershipPort = ownershipPorts.singlePort("분석과 사진 소유권 검증")
        if (boardAccessService.getById(command.boardId).userId != command.userId) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }

        val photoIds =
            command.stickers
                .flatMap { it.photoIds + listOfNotNull(it.sourcePhotoId) }
                .toSet()
        val scope =
            AnalysisPhotoOwnershipScope(
                userId = command.userId,
                boardId = command.boardId,
                analysisId = command.analysisId,
                photoIds = photoIds,
            )
        if (!ownershipPort.matches(scope)) {
            throw NotFoundException(StickerErrorCode.STICKER_NOT_FOUND)
        }
    }
}
