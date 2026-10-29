package com.github.nexters.ppotto.board.support

import com.github.nexters.ppotto.board.application.port.BoardInvitationPreviewStorage
import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import com.github.nexters.ppotto.support.ResettableFake
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@TestConfiguration(proxyBeanMethods = false)
class BoardInvitationTestConfig {
    @Bean
    @Primary
    fun boardInvitationPreviewStorage(): FakeBoardInvitationPreviewStorage = FakeBoardInvitationPreviewStorage()
}

class FakeBoardInvitationPreviewStorage :
    BoardInvitationPreviewStorage,
    ResettableFake {
    val uploaded = ConcurrentHashMap<String, ByteArray>()
    val deleted = CopyOnWriteArrayList<String>()
    var uploadFailure: RuntimeException? = null
    var deleteFailure: RuntimeException? = null
    var afterUpload: (() -> Unit)? = null

    override fun upload(
        invitationId: BoardInvitationId,
        bytes: ByteArray,
    ): String {
        uploadFailure?.let { throw it }
        val objectKey = "board-invitations/$invitationId/${UUID.randomUUID()}.webp"
        uploaded[objectKey] = bytes
        afterUpload?.invoke()
        return objectKey
    }

    override fun issueReadUrl(objectKey: String): String = "https://preview.test/$objectKey"

    override fun delete(objectKey: String) {
        deleteFailure?.let { throw it }
        deleted += objectKey
        uploaded.remove(objectKey)
    }

    override fun reset() {
        uploaded.clear()
        deleted.clear()
        uploadFailure = null
        deleteFailure = null
        afterUpload = null
    }
}

fun validWebp(
    width: Int = 624,
    height: Int = 540,
): ByteArray {
    require(width > 0 && height > 0)
    val payload = ByteArray(10)
    payload[3] = 0x9D.toByte()
    payload[4] = 0x01
    payload[5] = 0x2A
    payload[6] = (width and 0xFF).toByte()
    payload[7] = ((width shr 8) and 0x3F).toByte()
    payload[8] = (height and 0xFF).toByte()
    payload[9] = ((height shr 8) and 0x3F).toByte()
    return "RIFF".toByteArray() +
        littleEndian(22) +
        "WEBP".toByteArray() +
        "VP8 ".toByteArray() +
        littleEndian(payload.size) +
        payload
}

private fun littleEndian(value: Int): ByteArray =
    byteArrayOf(
        value.toByte(),
        (value shr 8).toByte(),
        (value shr 16).toByte(),
        (value shr 24).toByte(),
    )
