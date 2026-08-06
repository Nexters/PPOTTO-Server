package com.github.nexters.ppotto.analysis.infrastructure

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import java.util.UUID

class StickerObjectKeysTest {
    @Test
    fun `key is deterministic based on analysisId, themeIndex, and sourcePhotoId`() {
        val analysisId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val themeIndex = 0
        val sourcePhotoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")

        val key1 = StickerObjectKeys.keyFor(analysisId, themeIndex, sourcePhotoId)
        val key2 = StickerObjectKeys.keyFor(analysisId, themeIndex, sourcePhotoId)

        key1 shouldBe key2
    }

    @Test
    fun `key changes when analysisId changes`() {
        val analysisId1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val analysisId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440099")
        val themeIndex = 0
        val sourcePhotoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")

        val key1 = StickerObjectKeys.keyFor(analysisId1, themeIndex, sourcePhotoId)
        val key2 = StickerObjectKeys.keyFor(analysisId2, themeIndex, sourcePhotoId)

        key1 shouldNotBe key2
    }

    @Test
    fun `key changes when themeIndex changes`() {
        val analysisId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val sourcePhotoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")

        val key1 = StickerObjectKeys.keyFor(analysisId, 0, sourcePhotoId)
        val key2 = StickerObjectKeys.keyFor(analysisId, 1, sourcePhotoId)

        key1 shouldNotBe key2
    }

    @Test
    fun `key changes when sourcePhotoId changes`() {
        val analysisId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val themeIndex = 0
        val sourcePhotoId1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val sourcePhotoId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440099")

        val key1 = StickerObjectKeys.keyFor(analysisId, themeIndex, sourcePhotoId1)
        val key2 = StickerObjectKeys.keyFor(analysisId, themeIndex, sourcePhotoId2)

        key1 shouldNotBe key2
    }

    @Test
    fun `key format is stickers_analysisId_themeIndex-sourcePhotoId`() {
        val analysisId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val themeIndex = 2
        val sourcePhotoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")

        val key = StickerObjectKeys.keyFor(analysisId, themeIndex, sourcePhotoId)

        key shouldStartWith "stickers/"
        key shouldContain "550e8400-e29b-41d4-a716-446655440000"
        key shouldContain "2-550e8400"
        key shouldEndWith ".png"
    }

    @Test
    fun `재생성 key는 regenerationId가 같으면 동일하다`() {
        val stickerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val sourcePhotoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val regenerationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

        val key1 = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, regenerationId)
        val key2 = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, regenerationId)

        key1 shouldBe key2
    }

    @Test
    fun `재생성 key는 regenerationId가 다르면 달라진다`() {
        val stickerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val sourcePhotoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val regenerationId1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
        val regenerationId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440003")

        val key1 = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, regenerationId1)
        val key2 = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, regenerationId2)

        key1 shouldNotBe key2
    }

    @Test
    fun `재생성 key 형식은 stickers_stickerId_sourcePhotoId-regenerationId 이다`() {
        val stickerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val sourcePhotoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val regenerationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

        val key = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, regenerationId)

        key shouldBe "stickers/$stickerId/$sourcePhotoId-$regenerationId.png"
    }

    private infix fun String.shouldContain(substring: String) {
        this.contains(substring) shouldBe true
    }

    private infix fun String.shouldEndWith(suffix: String) {
        this.endsWith(suffix) shouldBe true
    }
}
