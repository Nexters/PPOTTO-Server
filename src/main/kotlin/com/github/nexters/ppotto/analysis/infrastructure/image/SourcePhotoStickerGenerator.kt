package com.github.nexters.ppotto.analysis.infrastructure.image

import com.github.nexters.ppotto.analysis.application.port.SourcePhotoImageReader
import com.github.nexters.ppotto.analysis.application.port.StickerBackgroundRemover
import com.github.nexters.ppotto.analysis.application.port.StickerGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SourcePhotoStickerGenerator(
    private val sourcePhotoImageReader: SourcePhotoImageReader,
    private val stickerBackgroundRemover: StickerBackgroundRemover,
    private val stickerImageCropper: StickerImageCropper,
) : StickerGenerator {
    override fun generate(
        sourceUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray {
        val sourceBytes = sourcePhotoImageReader.read(sourceUri)
        val removedBackgroundBytes = stickerBackgroundRemover.removeBackground(sourceBytes, sourceMimeType)
        val stickerBytes = stickerImageCropper.cropTransparentPadding(removedBackgroundBytes)
        log.info(
            "source photo sticker generated: sourceUri={}, sourceMimeType={}, targetSubject={}, sourceBytes={}, stickerBytes={}",
            sourceUri,
            sourceMimeType,
            targetSubject,
            sourceBytes.size,
            stickerBytes.size,
        )
        return stickerBytes
    }

    companion object {
        private val log = LoggerFactory.getLogger(SourcePhotoStickerGenerator::class.java)
    }
}
