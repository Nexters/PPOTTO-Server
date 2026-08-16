package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SourcePhotoStickerGenerator(
    private val sourcePhotoImageReader: SourcePhotoImageReader,
    private val stickerBackgroundRemover: StickerBackgroundRemover,
    private val stickerImageCropper: StickerImageCropper,
) : StickerGenerator {
    override fun generate(
        sourceGcsUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray {
        val sourceBytes = sourcePhotoImageReader.read(sourceGcsUri)
        val removedBackgroundBytes = stickerBackgroundRemover.removeBackground(sourceBytes, sourceMimeType)
        val stickerBytes = stickerImageCropper.cropTransparentPadding(removedBackgroundBytes)
        log.info(
            "source photo sticker generated: sourceGcsUri={}, sourceMimeType={}, targetSubject={}, sourceBytes={}, stickerBytes={}",
            sourceGcsUri,
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
