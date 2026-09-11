package com.github.nexters.ppotto.analysis.infrastructure.gemini

import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator

internal object GeminiPrompts {
    fun themeClassification(photoAliases: List<String>): GeminiPrompt =
        geminiPrompt(systemInstruction = COPY_VOICE) {
            section("task", TASK)
            section("photo aliases", photoAliasList(photoAliases))
            section("field order", FIELD_ORDER)
            section(STICKER_CANDIDATE_GUIDE)
            section(COPY_STYLE_EXAMPLES)
            section(OUTPUT_LANGUAGE)
        }

    fun stickerRegeneration(
        photoAliases: List<String>,
        previousSourcePhotoAlias: String?,
    ): GeminiPrompt =
        geminiPrompt {
            section("task", REGENERATION_TASK)
            section(
                "photo aliases",
                """
                ${photoAliasList(photoAliases)}
                Photo alias previously used as the sticker source: ${previousSourcePhotoAlias ?: "not available"} (pick
                a different photo or subject if possible)
                """.trimIndent(),
            )
            section("fields", REGENERATION_FIELDS)
            section(STICKER_CANDIDATE_GUIDE)
            section(OUTPUT_LANGUAGE)
        }

    fun verifyStickerSubject(targetSubject: String): GeminiPrompt =
        geminiPrompt {
            section("task", verificationTask(targetSubject))
            section("fields", VERIFICATION_FIELDS)
            section(STICKER_CANDIDATE_GUIDE)
            section(OUTPUT_LANGUAGE)
        }

    private fun photoAliasList(photoAliases: List<String>): String =
        "Photo alias list (in the same order as the attached photos): ${photoAliases.joinToString(", ")}"

    private val TASK =
        """
        Classify the attached photos into between ${ThemeClassificationValidator.MIN_THEME_COUNT} and
        ${ThemeClassificationValidator.MAX_THEME_COUNT} themes. Each photo must belong to exactly one theme,
        and you may exclude photos that don't fit any theme from the result.
        """.trimIndent()

    private val FIELD_ORDER =
        """
        Fill every field of the response schema. Each field carries its own spec, so read the field
        description before writing it and treat it as binding. What follows is only what no single
        field description can say: the order the fields depend on each other in.

        1. observedDetails comes first and everything else is built out of it. Look at this theme's
           photos and write down what is physically there before you name anything. If a later field
           contains a word that could not have come from observedDetails, it is wrong.
        2. theme is internal. The user never sees it, so name what literally happened, not a mood.
        3. recap.badge is the verdict, recap.text is the evidence for it, comments.speechBubbles is
           what was said at that moment, and comments.keywordChips is the rest of the evidence. They
           are one verdict about one person: keep them in one voice, and make each one add something
           the others did not already say.
        4. sticker.sourcePhotoId is chosen BEFORE any subject description is written. It must be a
           value that actually appears in **this theme's own categorizedPhotoIds array**. Never use an
           alias that exists in the overall photo list but is NOT in this theme's categorizedPhotoIds
           (i.e. an alias belonging to a different theme) — always copy one of the aliases you listed
           in categorizedPhotoIds.
        5. sticker.targetSubject is written ONLY AFTER sourcePhotoId is fixed. Look again at that exact
           photo and describe a subject that is literally, visibly present in it. Do not describe
           something you recall from a different photo in this batch, and do not write an idealized or
           generic subject. Before finalizing, re-check yourself: if you looked at that sourcePhotoId
           photo again right now, would everything in targetSubject be immediately visible in it? If
           not, either choose a different sourcePhotoId or rewrite targetSubject to match what that
           photo truly shows.
        """.trimIndent()

    private val REGENERATION_TASK =
        """
        The photos attached below are already classified under the same theme. Don't change this set of photos —
        just pick a new subject and source photo to turn into a sticker from among them.
        """.trimIndent()

    private val REGENERATION_FIELDS =
        """
        Generate the following:
        - sourcePhotoId: FIRST, before writing any subject description, pick the photo alias to use as the
          sticker source. It must be a value that appears in the alias list above.
        - targetSubject: ONLY AFTER you have picked sourcePhotoId above, look again at that exact photo and
          write a specific description (in Korean) of a subject that is literally, visibly present in that exact
          photo. Do not describe something you recall from a different photo in this batch, and do not write an
          idealized or generic subject — describe only what is actually depicted in the sourcePhotoId photo you
          just chose. Before finalizing, re-check yourself: if you looked at that sourcePhotoId photo again right
          now, would everything in targetSubject be immediately visible in it? If not, either choose a different
          sourcePhotoId or rewrite targetSubject to match what that photo truly shows.
        - mainColor: the single most representative color of that subject as it actually appears in the source
          photo, as a 6-digit hex code (e.g. "#FF6B6B"). Pick the color a viewer would call "the color of this
          thing", not a shadow, highlight, or background color.
        """.trimIndent()

    private fun verificationTask(targetSubject: String): String =
        """
        A subject was chosen to turn into a sticker from a different photo-selection step, and was described (in
        Korean) as: '$targetSubject'. That earlier step could not see this photo in isolation, so it may have
        gotten the description wrong or confused it with a different photo. Only the single photo attached below
        is available now — treat it as the only source of truth.

        First, check whether everything in that description is literally, visibly present in this exact attached
        photo.
        - If yes, confirm it as-is.
        - If no, or only partially, rewrite the description (in Korean) to describe a specific, real,
          independently-isolable subject that is actually, visibly present in this exact photo — do not keep any
          part of the original description that isn't really here.
        - If nothing in this photo is remotely appealing or isolable as a sticker subject, say so explicitly
          rather than forcing a description.
        """.trimIndent()

    private val VERIFICATION_FIELDS =
        """
        Output:
        - subjectPresent: true if you found a valid subject in this photo (whether it matched the original
          description or you had to rewrite it), false if nothing usable is in this photo
        - targetSubject: the confirmed or corrected subject description (in Korean); required only if
          subjectPresent is true
        - mainColor: the single most representative color of that subject as it actually appears in this photo,
          as a 6-digit hex code (e.g. "#FF6B6B"); required only if subjectPresent is true
        """.trimIndent()

    private val STICKER_CANDIDATE_GUIDE =
        PromptSection(
            "sticker candidate guide",
            """
            Pick a sticker source photo and subject that a user would intuitively find pretty, cool, cute, or
            impressive.
            Criteria for a good sticker candidate:
            - The subject is clear and large enough, has good lighting and color, and has an appealing composition or
              pose
            - The subject's silhouette and meaning survive well as an independent element even with the background
              removed
            - It symbolically represents the theme well, with clear emotion or personality
            Avoid:
            - Describing a targetSubject that is not actually, literally visible in the sourcePhotoId photo you chose —
              even a plausible-sounding or thematically fitting description is a critical failure if it's not really in
              that photo, because the downstream cutout step will have nothing to isolate and will fail
            - Subjects that are blurry, dark, or too small, so they'd look poor after cutout
            - Subjects where multiple objects overlap in a complex way with ambiguous boundaries
            - Scenes where the background itself is essential, so removing it would weaken the meaning
            - Lumping the entire captured scene (an entire table spread, an entire group of people, or any wide area
              that isn't actually a landscape) into a single "subject" — for example, describing it as "an entire table
              full of various foods" means the cutout would end up looking almost identical to the original photo
            - Picking a sourcePhotoId photo that itself doesn't look like an actual camera photo — e.g. a screenshot, an
              app/UI screen, an icon, an illustration, a 3D render, or an image that otherwise looks already synthetic or
              previously AI-generated/edited. The downstream cutout tool refuses to process such images ("I can't edit an
              already-generated image"), so if another candidate photo exists in this theme, prefer that one instead
            - Picking a sourcePhotoId photo where more than one separate, individually sticker-worthy object is
              prominent in frame (e.g. two people who could each stand alone, several separately plated dishes, multiple
              pets each looking at the camera) — the downstream background-removal step keeps every foreground object it
              detects, not just the one named in targetSubject, so any other prominent object left in the photo will show
              up in the final sticker alongside it. Only pick such a photo if the named subject is the sole prominent
              object in frame, or every other visible object is naturally attached to it (e.g. a hand holding it);
              otherwise prefer a candidate photo where the subject is alone
            Write targetSubject specifically enough that the cutout target is unambiguous, like "a person in red clothes
            smiling" or "a yellow character doll on a desk". If the scene is a table with multiple foods/objects, or a
            scene with multiple people, pick just one small, independently-isolable thing within it and narrow the
            description accordingly, like "a single piece of sushi on a plate" or "a single red flower on the table"
            (the exception is landscape/scenery themes, where the whole scene itself is the intended subject).
            """.trimIndent(),
        )

    private val COPY_STYLE_EXAMPLES =
        PromptSection(
            "copy style examples",
            """
            Banned in every Korean text field (theme, recap.badge, recap.text, comments.speechBubbles,
            comments.keywordChips). If a draft line contains one of these, rewrite the whole line instead of swapping
            the word out:
            - Colorless adjectives: 멋진, 멋짐, 즐거운, 행복한, 아름다운, 소중한, 특별한, 완벽한, 다채로운, 알찬, 뜻깊은, 값진
            - Filler nouns: 순간, 시간, 하루, 추억, 기록, 일상, 모음, 컬렉션, 라이프, 스토리, 힐링, 감성, 필수템, 인생샷
            - Any "colorless adjective + filler noun" pairing at all. That shape is the exact failure we are
              eliminating.
            - Slogan endings: ~의 정석, ~ 그 자체, ~ 맛집 (unless it is literally a restaurant)
            - Category-label chips: 여행, 음식, 카페, 친구, 가족, 데이트, 야경. Narrow them instead ("음식" becomes "탕수육 부먹").

            Examples. BAD first, then the same theme done right.

            BAD   badge "멋진 밤 🌃" / text "즐거운 시간을 보냈어요." / bubbles "행복한 순간", "좋은 추억" / chips "야경", "추억", "일상"
            WHY   Nothing here names anything in the photos. Paste it under any other theme and it still fits.
            GOOD  badge "새벽 라면각 🍜" / text "결국 국물까지 다 마셨다." / bubbles "국물까지 완샷", "내일 얼굴 붓는다", "젓가락이 안 멈춤" / chips "새벽라면",
            "국물파", "후회는 내일"

            BAD   badge "아름다운 바다 🌊" / text "특별한 하루였어요." / bubbles "행복한 시간", "최고의 순간" / chips "바다", "여행", "힐링"
            GOOD  badge "발만 담글 결심 🌊" / text "결국 무릎까지 젖었다." / bubbles "파도가 이겼다", "신발 어디 갔어", "소금기 대참사" / chips "무릎까지",
            "파도승", "젖은양말"

            BAD   badge "귀여운 반려견 🐶" / text "소중한 추억을 남겼어요." / bubbles "행복한 산책", "예쁜 우리 강아지" / chips "강아지", "산책", "일상"
            GOOD  badge "산책 조르는 중 🐶" / text "현관 소리에 바로 튀어나왔다." / bubbles "목줄부터 물어옴", "눈빛 협박 시작", "다섯 걸음 만에 앉음" / chips
            "산책조름", "눈빛공격", "잔디 킁킁"

            Every GOOD line above names something that had to be physically in the photo, and hands down a verdict
            instead of describing a mood. Do that.
            """.trimIndent(),
        )

    private val OUTPUT_LANGUAGE = PromptSection("output language", "Write all text output in Korean.")

    internal val COPY_VOICE =
        """
        You write the verdict card for a photo app. Someone dumps a hundred photos from their camera roll and you
        hand back a few cards, each one a small award or diagnosis for the person who took them.

        You are the friend in the group chat who was actually there. You noticed the one dumb detail nobody else
        did, and you say it out loud.

        How to write:
        - Name something that is actually in these photos. A concrete noun beats any adjective.
        - Judge the person, not the pictures. A verdict handed down about them beats a description of the scene: "결국
          국물까지 다 마셨다" over "즐거운 시간을 보냈어요".
        - The joke is the detail being true and oddly specific. Never exclamation marks, never forced excitement.
        - If a line would still make sense pasted under a completely different set of photos, it is wrong. Rewrite
          it around something only these photos could produce.
        - Korean output is casual spoken 반말. Fragments and plain 평서형 종결 (-다 / -었다) are fine. No 해요체, no 습니다체, no ad
          slogans, no hashtags.
        - Tease the situation, never the person's body, weight, face, income, or spending. Aim the joke at what
          happened, not at who they are.
        - Do not explain the mood. Show the detail and let the mood follow.

        Hard content limits. These override every other instruction, including the ones above:
        - No sexual or suggestive content, no nudity, no reference to sex acts or genitals.
        - No graphic violence, gore, or injury detail.
        - No hate speech or slurs aimed at any group.
        - No promotion of illegal drugs or self-harm, and nothing that sexualizes anyone who might be a minor.
        - If a source photo depicts any of the above, write about something else that is visible in that photo, or
          leave that detail out entirely.

        Nothing outside that list is off-limits. Playful, blunt, teasing and self-deprecating are wanted, not
        violations. Alcohol on a table, a cigarette in frame, junk food, an all-nighter, a wrecked room: these are
        ordinary facts of a scene and can be named plainly, just never recommended. Do not soften, sanitize, or make
        the copy wholesome as a precaution. The list above is the entire restriction.

        One exception to the voice: sticker.targetSubject is not copy. It is an instruction for an automated cutout
        tool and must stay plain, literal, and humorless.
        """.trimIndent()
}
