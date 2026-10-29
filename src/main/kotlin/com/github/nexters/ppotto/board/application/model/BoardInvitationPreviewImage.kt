package com.github.nexters.ppotto.board.application.model

import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.global.error.InvalidInputException

class BoardInvitationPreviewImage private constructor(
    val bytes: ByteArray,
) {
    companion object {
        const val MAX_SIZE_BYTES = 1024 * 1024
        const val MAX_WIDTH = 624
        const val MAX_HEIGHT = 540

        fun from(bytes: ByteArray): BoardInvitationPreviewImage {
            if (bytes.isEmpty() || bytes.size > MAX_SIZE_BYTES) invalidPreview()
            val size = WebpSize.read(bytes) ?: invalidPreview()
            if (size.width !in 1..MAX_WIDTH || size.height !in 1..MAX_HEIGHT) {
                invalidPreview()
            }
            return BoardInvitationPreviewImage(bytes.copyOf())
        }

        private fun invalidPreview(): Nothing = throw InvalidInputException(BoardErrorCode.INVALID_INVITATION_PREVIEW)
    }
}

private data class WebpSize(
    val width: Int,
    val height: Int,
) {
    companion object {
        fun read(bytes: ByteArray): WebpSize? = readRiffEnd(bytes)?.let { readImageSize(bytes, it) }

        private fun readRiffEnd(bytes: ByteArray): Int? {
            if (!bytes.hasWebpHeader()) return null
            val riffSize = bytes.uint32LittleEndian(4)
            val riffEnd = riffSize + RIFF_SIZE_FIELD_END
            return riffEnd
                .takeIf { riffSize >= MIN_RIFF_SIZE && it == bytes.size.toLong() }
                ?.toInt()
        }

        private fun readImageSize(
            bytes: ByteArray,
            riffEnd: Int,
        ): WebpSize? {
            var offset = RIFF_HEADER_SIZE
            var extendedSize: WebpSize? = null
            var encodedSize: WebpSize? = null
            var valid = true
            while (valid && encodedSize == null && offset + CHUNK_HEADER_SIZE <= riffEnd) {
                val chunk = readChunk(bytes, offset, riffEnd)
                if (chunk == null) {
                    valid = false
                    continue
                }
                when (chunk.type) {
                    "VP8X" -> {
                        extendedSize = readVp8x(bytes, chunk.dataOffset, chunk.size)
                        valid = extendedSize != null
                    }
                    "VP8L" -> {
                        encodedSize = readVp8l(bytes, chunk.dataOffset, chunk.size)
                        valid = encodedSize != null
                    }
                    "VP8 " -> {
                        encodedSize = readVp8(bytes, chunk.dataOffset, chunk.size)
                        valid = encodedSize != null
                    }
                }
                offset = chunk.nextOffset
            }
            return encodedSize?.takeIf { valid }?.let { extendedSize ?: it }
        }

        private fun readVp8x(
            bytes: ByteArray,
            offset: Int,
            size: Int,
        ): WebpSize? =
            if (size < VP8X_HEADER_SIZE) {
                null
            } else {
                WebpSize(
                    width = bytes.uint24LittleEndian(offset + 4) + 1,
                    height = bytes.uint24LittleEndian(offset + 7) + 1,
                )
            }

        private fun readVp8l(
            bytes: ByteArray,
            offset: Int,
            size: Int,
        ): WebpSize? {
            if (size < VP8L_HEADER_SIZE || bytes[offset].toUnsignedInt() != VP8L_SIGNATURE) return null
            val width = 1 + bytes[offset + 1].toUnsignedInt() + ((bytes[offset + 2].toUnsignedInt() and 0x3F) shl 8)
            val height =
                1 +
                    ((bytes[offset + 2].toUnsignedInt() and 0xC0) shr 6) +
                    (bytes[offset + 3].toUnsignedInt() shl 2) +
                    ((bytes[offset + 4].toUnsignedInt() and 0x0F) shl 10)
            return WebpSize(width, height)
        }

        private fun readVp8(
            bytes: ByteArray,
            offset: Int,
            size: Int,
        ): WebpSize? {
            if (size < VP8_HEADER_SIZE || !bytes.hasVp8Signature(offset)) {
                return null
            }
            val width = bytes.uint16LittleEndian(offset + 6) and VP8_DIMENSION_MASK
            val height = bytes.uint16LittleEndian(offset + 8) and VP8_DIMENSION_MASK
            return WebpSize(width, height)
        }
    }
}

private data class WebpChunk(
    val type: String,
    val dataOffset: Int,
    val size: Int,
    val nextOffset: Int,
)

private fun readChunk(
    bytes: ByteArray,
    offset: Int,
    riffEnd: Int,
): WebpChunk? {
    val chunkSize = bytes.uint32LittleEndian(offset + 4)
    val dataOffset = offset + CHUNK_HEADER_SIZE
    if (chunkSize > Int.MAX_VALUE || dataOffset.toLong() + chunkSize > riffEnd.toLong()) return null
    val size = chunkSize.toInt()
    return WebpChunk(
        type = bytes.ascii(offset, 4),
        dataOffset = dataOffset,
        size = size,
        nextOffset = dataOffset + size + (size and 1),
    )
}

private fun ByteArray.hasWebpHeader(): Boolean =
    size >= RIFF_HEADER_SIZE &&
        ascii(0, 4) == "RIFF" &&
        ascii(8, 4) == "WEBP"

private fun ByteArray.hasVp8Signature(offset: Int): Boolean =
    this[offset + 3].toUnsignedInt() == VP8_SIGNATURE_FIRST &&
        this[offset + 4].toUnsignedInt() == VP8_SIGNATURE_SECOND &&
        this[offset + 5].toUnsignedInt() == VP8_SIGNATURE_THIRD

private fun ByteArray.ascii(
    offset: Int,
    length: Int,
): String = String(this, offset, length, Charsets.US_ASCII)

private fun ByteArray.uint16LittleEndian(offset: Int): Int = this[offset].toUnsignedInt() or (this[offset + 1].toUnsignedInt() shl 8)

private fun ByteArray.uint24LittleEndian(offset: Int): Int = uint16LittleEndian(offset) or (this[offset + 2].toUnsignedInt() shl 16)

private fun ByteArray.uint32LittleEndian(offset: Int): Long =
    uint24LittleEndian(offset).toLong() or (this[offset + 3].toUnsignedInt().toLong() shl 24)

private fun Byte.toUnsignedInt(): Int = toInt() and 0xFF

private const val RIFF_HEADER_SIZE = 12
private const val RIFF_SIZE_FIELD_END = 8
private const val MIN_RIFF_SIZE = 4L
private const val CHUNK_HEADER_SIZE = 8
private const val VP8X_HEADER_SIZE = 10
private const val VP8L_HEADER_SIZE = 5
private const val VP8_HEADER_SIZE = 10
private const val VP8L_SIGNATURE = 0x2F
private const val VP8_SIGNATURE_FIRST = 0x9D
private const val VP8_SIGNATURE_SECOND = 0x01
private const val VP8_SIGNATURE_THIRD = 0x2A
private const val VP8_DIMENSION_MASK = 0x3FFF
