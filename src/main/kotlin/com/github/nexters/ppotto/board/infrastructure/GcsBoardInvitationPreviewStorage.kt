package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.board.application.port.BoardInvitationPreviewStorage
import com.github.nexters.ppotto.global.config.GcsProperties
import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import com.github.nexters.ppotto.global.storage.GcsReadUrlIssuer
import com.github.nexters.ppotto.global.storage.ObjectStorageCleaner
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Component

@Component
class GcsBoardInvitationPreviewStorage(
    private val storage: Storage,
    private val gcsProperties: GcsProperties,
    private val gcsReadUrlIssuer: GcsReadUrlIssuer,
    private val objectStorageCleaner: ObjectStorageCleaner,
) : BoardInvitationPreviewStorage {
    override fun upload(
        invitationId: BoardInvitationId,
        bytes: ByteArray,
    ): String {
        val objectKey = BoardInvitationPreviewObjectKeys.keyFor(invitationId)
        val blobInfo =
            BlobInfo
                .newBuilder(BlobId.of(gcsProperties.bucket, objectKey))
                .setContentType(WEBP_CONTENT_TYPE)
                .build()
        storage.create(blobInfo, bytes)
        return objectKey
    }

    override fun issueReadUrl(objectKey: String): String = gcsReadUrlIssuer.issue(listOf(objectKey)).getValue(objectKey)

    override fun delete(objectKey: String) {
        objectStorageCleaner.deleteAll(listOf(objectKey))
    }

    private companion object {
        const val WEBP_CONTENT_TYPE = "image/webp"
    }
}
