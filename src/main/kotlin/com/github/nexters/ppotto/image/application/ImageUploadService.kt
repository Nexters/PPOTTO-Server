package com.github.nexters.ppotto.image.application

import com.github.nexters.ppotto.board.application.BoardQueryService
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.image.domain.Image
import com.github.nexters.ppotto.image.domain.ImageErrorCode
import com.github.nexters.ppotto.image.domain.ImageUploadUrlIssuer
import com.github.nexters.ppotto.image.domain.UploadStatus
import com.github.nexters.ppotto.image.infrastructure.ImageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ImageUploadService(
    private val imageRepository: ImageRepository,
    private val boardQueryService: BoardQueryService,
    private val imageUploadUrlIssuer: ImageUploadUrlIssuer,
) {
    @Transactional
    fun issueUploadUrls(
        boardId: UUID,
        fileNames: List<String>,
    ): ImageUploadUrlsResult {
        boardQueryService.getById(boardId)

        val contentTypesByFileName = fileNames.associateWith { resolveContentType(it) }
        val uploadSessionId = UUID.randomUUID()

        val items =
            fileNames.map { fileName ->
                val image = imageRepository.save(boardId, uploadSessionId, fileName)
                val objectKey = "images/${image.id}/${sanitize(fileName)}"
                val uploadUrl = imageUploadUrlIssuer.issue(objectKey, contentTypesByFileName.getValue(fileName))
                ImageUploadUrlItemResult(image.id, fileName, uploadUrl)
            }

        return ImageUploadUrlsResult(uploadSessionId, items)
    }

    @Transactional
    fun completeUpload(
        boardId: UUID,
        imageId: UUID,
        newStatus: UploadStatus,
    ): Image {
        val image = imageRepository.findById(imageId)?.takeIf { it.boardId == boardId } ?: throw NotFoundException()

        return imageRepository.updateStatus(imageId, UploadStatus.PENDING, newStatus)
            ?: throw ConflictException(ImageErrorCode.ALREADY_PROCESSED_UPLOAD)
    }

    private fun resolveContentType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return CONTENT_TYPES_BY_EXTENSION[extension] ?: throw InvalidInputException(ImageErrorCode.UNSUPPORTED_FILE_EXTENSION)
    }

    private fun sanitize(fileName: String): String = fileName.replace(SANITIZE_PATTERN, "_")

    companion object {
        private val SANITIZE_PATTERN = Regex("[^A-Za-z0-9._-]")
        private val CONTENT_TYPES_BY_EXTENSION =
            mapOf(
                "jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "png" to "image/png",
                "webp" to "image/webp",
                "heic" to "image/heic",
                "heif" to "image/heif",
            )
    }
}
