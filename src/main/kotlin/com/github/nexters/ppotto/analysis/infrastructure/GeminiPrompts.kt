package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
import java.util.UUID

object GeminiPrompts {
    fun stickerCutout(targetSubject: String): String =
        """
        Perform a precise background removal (like a Photoshop "cutout" / "remove background" tool) on this photo, keeping only this subject: '$targetSubject'. This is NOT a printed die-cut sticker — do not apply any printed-sticker styling such as a white border, colored outline, or halo around the edge. Treat this purely as isolating the subject from its background, nothing more.
        Preserve the subject's natural appeal from the original photo, including its pose, expression, color, texture, and recognizable silhouette.
        Keep the cutout clean and polished, but do not redraw, cartoonize, beautify unrealistically, or add new design elements.

        Background: unless the subject description above explicitly refers to a landscape, scenery, or wide view, treat everything else in the original photo as background and remove it completely — this includes walls, floors, furniture, other objects, other people, sky, ground, or any part of the scene not covered by the named subject itself. Only when the subject description itself is a landscape/scenery should a wider view remain, and even then only that described scene, not unrelated clutter around it.

        Orientation: keep the subject's overall up-down axis exactly as gravity would place it in real life. This is only about that axis, not the subject's pose — a sitting, lying, or reclining pose is fine. Do not rotate, tilt, flip, or invert the image so the subject reads as sideways or upside down — for example, a potted plant must stand upright, not sideways or upside down, and a standing person must have feet down and head up, not appear to stand on the ceiling.

        Edges: absolutely no outline, border, stroke, halo, or colored line of any kind may trace the subject's silhouette — not white, not any color. The cutout boundary itself must be the only edge, exactly like a background-removal tool would produce, not like a printed sticker. This applies especially to people: their body, hair, and clothing edges must transition directly from subject to background with zero added line art.

        Output format: the image must be a PNG cropped tightly to the subject's silhouette, with a genuine alpha-transparent background — every pixel outside the cutout must have alpha=0. Do not fill that area with white, gray, black, or any solid-color pixels, and do not bake in a gray/white checkerboard pattern as real pixels (that checkerboard is only an image-editor convention for showing transparency, never actual output content). Do not place the subject on any backdrop, canvas, frame, drop shadow, or decorative element, and do not leave a large rectangular area of transparent padding around it as if it still sits inside the original photo's frame — the result should read as a pure background-removed cutout, not a photo with a decorative border.
        """.trimIndent()

    fun themeClassification(photoIds: List<UUID>): String =
        listOf(
            """
            아래에 첨부된 사진들을 최대 ${ThemeClassificationValidator.MAX_THEME_COUNT} 개의 테마로 분류해줘. 각 사진은 정확히 하나의 테마에만 속해야 하고,
            어느 테마에도 어울리지 않는 사진은 결과에서 제외해도 돼.
            """.trimIndent(),
            "사진 목록(순서대로): ${photoIds.joinToString(", ")}",
            """
            각 테마에 대해 다음을 생성해줘:
            - theme: 테마 이름 (한국어)
            - categorizedPhotoIds: 이 테마로 분류된 사진 id 목록 (위 목록에 있는 값만 사용)
            - recap.badge: 8자 내외의 짧은 뱃지 문구 (한국어)
            - recap.text: 1~2문장의 리캡 문구 (한국어)
            - sticker.targetSubject: 스티커로 만들 피사체에 대한 구체적인 설명 (한국어)
            - sticker.sourcePhotoId: 스티커의 원본으로 쓸 사진 id. 반드시 이 테마의 categorizedPhotoIds 안에 있는 값이어야 함.
            """.trimIndent(),
            STICKER_CANDIDATE_GUIDE,
            OUTPUT_LANGUAGE_NOTE,
        ).joinToString("\n\n")

    fun stickerRegeneration(
        photoIds: List<UUID>,
        previousSourcePhotoId: UUID,
    ): String =
        listOf(
            """
            아래에 첨부된 사진들은 이미 같은 테마로 분류되어 있어. 이 사진 구성은 바꾸지 말고,
            이 중에서 스티커로 만들 피사체와 원본 사진만 새로 골라줘.
            """.trimIndent(),
            """
            사진 목록(순서대로): ${photoIds.joinToString(", ")}
            이전에 스티커 원본으로 썼던 사진 id: $previousSourcePhotoId (가능하면 다른 사진이나 다른 피사체를 골라줘)
            """.trimIndent(),
            """
            다음을 생성해줘:
            - targetSubject: 스티커로 만들 피사체에 대한 구체적인 설명 (한국어)
            - sourcePhotoId: 스티커의 원본으로 쓸 사진 id. 반드시 위 사진 목록 안에 있는 값이어야 함.
            """.trimIndent(),
            STICKER_CANDIDATE_GUIDE,
            OUTPUT_LANGUAGE_NOTE,
        ).joinToString("\n\n")

    private val STICKER_CANDIDATE_GUIDE =
        """
        스티커 원본 사진과 피사체는 사용자가 직관적으로 예쁘다, 멋지다, 귀엽다, 인상적이다고 느낄 만한 것을 골라줘.
        좋은 스티커 후보를 고르는 기준:
        - 피사체가 선명하고 충분히 크며, 조명과 색감이 좋고, 구도나 포즈가 매력적임
        - 배경을 제거해도 피사체의 실루엣과 의미가 독립적으로 잘 살아남음
        - 테마를 상징적으로 잘 보여주고, 감정이나 개성이 잘 드러남
        피해야 할 후보:
        - 흐리거나 어둡거나 너무 작아서 누끼 후 볼품없어지는 피사체
        - 여러 물체가 복잡하게 겹쳐 경계가 애매한 피사체
        - 배경이 핵심이라 누끼를 따면 의미가 약해지는 장면
        targetSubject는 누끼 대상이 정확히 드러나도록 "빨간 옷을 입고 웃는 사람", "책상 위의 노란 캐릭터 인형"처럼 구체적으로 작성해줘.
        """.trimIndent()

    private const val OUTPUT_LANGUAGE_NOTE = "모든 텍스트 출력은 한국어로 작성해줘."
}
