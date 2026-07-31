package com.github.nexters.ppotto

import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.jooq.tables.references.PHOTOS
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.application.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.application.SaveAnalysisResultCommand
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.UserJourneyTestConfig
import com.github.nexters.ppotto.user.application.WithdrawnUserCleanupService
import com.jayway.jsonpath.JsonPath
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.hamcrest.Matchers.hasItem
import org.jooq.DSLContext
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

private const val PHOTO_COUNT = 90
private const val STICKER_IMAGE_KEY = "stickers/journey-result.png"

@AutoConfigureMockMvc
@Import(UserJourneyTestConfig::class, AnalysisTestConfig::class)
class UserJourneyIntegrationTest(
    mockMvc: MockMvc,
    analysisResultSaveService: AnalysisResultSaveService,
    cleanupService: WithdrawnUserCleanupService,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("현재 필수 약관이 시행 중이고 가입한 적 없는 소셜 계정이 있을 때") {
            val termId =
                dslContext
                    .insertInto(TERMS, TERMS.CODE, TERMS.VERSION, TERMS.IS_REQUIRED, TERMS.CONTENT_URL, TERMS.EFFECTIVE_AT)
                    .values("JOURNEY-${UUID.randomUUID()}", "1.0", true, "https://example.com/terms", Instant.now().minusSeconds(60))
                    .returning(TERMS.ID)
                    .fetchOne(TERMS.ID)!!
            val providerUserId = "journey-${UUID.randomUUID()}"

            When("로그인부터 탈퇴와 정리 배치까지 API 순서대로 호출하면") {
                Then("가입, 약관, 보드, 분석, 리캡, 스티커 편집, 탈퇴가 계약대로 이어진다") {
                    val login =
                        mockMvc
                            .perform(
                                post("/auth/login")
                                    .header("X-API-Version", "1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""{"provider":"KAKAO","accessToken":"$providerUserId"}"""),
                            ).andExpect(status().isOk)
                            .andExpect(jsonPath("$.data.isNewUser").value(true))
                            .andExpect(jsonPath("$.data.pendingTerms[?(@.id == '$termId')].agreed").value(hasItem(false)))
                            .andReturn()
                            .response
                            .getContentAsString(Charsets.UTF_8)
                    val accessToken = JsonPath.read<String>(login, "$.data.accessToken")
                    val refreshToken = JsonPath.read<String>(login, "$.data.refreshToken")
                    val pendingTermIds = JsonPath.read<List<String>>(login, "$.data.pendingTerms[*].id")

                    mockMvc
                        .perform(get("/terms").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data[?(@.id == '$termId')].agreed").value(hasItem(false)))

                    mockMvc
                        .perform(
                            post("/terms/agreements")
                                .authorized(accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    pendingTermIds.joinToString(
                                        prefix = "{\"termIds\":[\"",
                                        postfix = "\"]}",
                                        separator = "\",\"",
                                    ),
                                ),
                        ).andExpect(status().isOk)

                    mockMvc
                        .perform(get("/terms").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data[?(@.id == '$termId')].agreed").value(hasItem(true)))

                    val userId =
                        mockMvc
                            .perform(get("/users/me").authorized(accessToken))
                            .andExpect(status().isOk)
                            .andExpect(jsonPath("$.data.provider").value("KAKAO"))
                            .andReturn()
                            .response
                            .getContentAsString(Charsets.UTF_8)
                            .let { UUID.fromString(JsonPath.read<String>(it, "$.data.id")) }

                    mockMvc
                        .perform(get("/boards").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.length()").value(1))

                    val boardId =
                        mockMvc
                            .perform(
                                post("/boards")
                                    .authorized(accessToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""{"name":"여행 보드"}"""),
                            ).andExpect(status().isOk)
                            .andExpect(jsonPath("$.data.name").value("여행 보드"))
                            .andReturn()
                            .response
                            .getContentAsString(Charsets.UTF_8)
                            .let { UUID.fromString(JsonPath.read<String>(it, "$.data.id")) }

                    val created =
                        mockMvc
                            .perform(
                                post("/analysis")
                                    .authorized(accessToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(createAnalysisBody(boardId)),
                            ).andExpect(status().isOk)
                            .andExpect(jsonPath("$.data.uploads.length()").value(PHOTO_COUNT))
                            .andReturn()
                            .response
                            .getContentAsString(Charsets.UTF_8)
                    val analysisId = UUID.fromString(JsonPath.read<String>(created, "$.data.analysisId"))
                    val photoIds =
                        JsonPath
                            .read<List<String>>(created, "$.data.uploads[*].photoId")
                            .map(UUID::fromString)

                    mockMvc
                        .perform(post("/analysis/$analysisId/start").authorized(accessToken))
                        .andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.data.uploadedCount").value(PHOTO_COUNT))
                        .andExpect(jsonPath("$.data.failedCount").value(0))

                    val stickerIds =
                        analysisResultSaveService
                            .save(
                                SaveAnalysisResultCommand(
                                    userId = userId,
                                    analysisId = analysisId,
                                    boardId = boardId,
                                    stickers = analysisStickerResults(photoIds),
                                ),
                            ).stickerIds
                    stickerIds shouldHaveSize 2

                    mockMvc
                        .perform(get("/boards/$boardId").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.stickers.length()").value(2))
                        .andExpect(jsonPath("$.data.stickers[0].id").value(stickerIds.first().toString()))
                        .andExpect(jsonPath("$.data.stickers[0].type").value("IMAGE"))
                        .andExpect(jsonPath("$.data.stickers[0].isNew").value(true))
                        .andExpect(jsonPath("$.data.stickers[0].posX").value(10.0))
                        .andExpect(jsonPath("$.data.stickers[1].type").value("TEXT"))

                    mockMvc
                        .perform(get("/stickers/${stickerIds.first()}").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.comments.length()").value(1))
                        .andExpect(jsonPath("$.data.comments[0].content").value("함께한 여름"))
                        .andExpect(jsonPath("$.data.photos.length()").value(2))
                        .andReturn()
                        .response
                        .getContentAsString(Charsets.UTF_8)
                        .let { JsonPath.read<String>(it, "$.data.photos[0].imageUrl") }
                        .shouldStartWith("https://fake-read-url/photos/$analysisId/")

                    mockMvc
                        .perform(post("/stickers/${stickerIds.first()}/view").authorized(accessToken))
                        .andExpect(status().isOk)

                    mockMvc
                        .perform(
                            patch("/stickers/${stickerIds.first()}")
                                .authorized(accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"title":"제주 여행"}"""),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.title").value("제주 여행"))

                    mockMvc
                        .perform(delete("/stickers/${stickerIds.last()}").authorized(accessToken))
                        .andExpect(status().isOk)

                    mockMvc
                        .perform(get("/boards/$boardId").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.stickers.length()").value(1))
                        .andExpect(jsonPath("$.data.stickers[0].title").value("제주 여행"))
                        .andExpect(jsonPath("$.data.stickers[0].isNew").value(false))

                    mockMvc
                        .perform(delete("/users/me").authorized(accessToken))
                        .andExpect(status().isOk)

                    mockMvc
                        .perform(get("/users/me").authorized(accessToken))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("USER-001"))

                    mockMvc
                        .perform(
                            post("/auth/refresh")
                                .header("X-API-Version", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"refreshToken":"$refreshToken"}"""),
                        ).andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("AUTH-002"))

                    cleanupService
                        .cleanup(deletedBefore = Instant.now().plusSeconds(1), batchSize = 10)
                        .deletedUserIds shouldContainExactly listOf(userId)

                    dslContext.fetchExists(STICKERS, STICKERS.BOARD_ID.eq(boardId)) shouldBe false
                    dslContext.fetchExists(PHOTOS, PHOTOS.ANALYSIS_ID.eq(analysisId)) shouldBe false
                    dslContext.fetchExists(ANALYSIS, ANALYSIS.USER_ID.eq(userId)) shouldBe false
                    dslContext.fetchExists(BOARDS, BOARDS.USER_ID.eq(userId)) shouldBe false
                    dslContext.fetchExists(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(userId)) shouldBe false
                    dslContext.fetchExists(USERS, USERS.ID.eq(userId)) shouldBe false
                }
            }
        }
    })

private fun MockHttpServletRequestBuilder.authorized(accessToken: String): MockHttpServletRequestBuilder =
    header("Authorization", "Bearer $accessToken")
        .header("X-API-Version", "1")

private fun createAnalysisBody(boardId: UUID): String =
    (0 until PHOTO_COUNT)
        .joinToString(",") {
            """{"takenAt":"${Instant.parse("2026-07-01T00:00:00Z").plusSeconds(it.toLong())}","contentType":"image/jpeg"}"""
        }.let { """{"boardId":"$boardId","photos":[$it]}""" }

private fun analysisStickerResults(photoIds: List<UUID>): List<AnalysisStickerResult> =
    listOf(
        AnalysisStickerResult(
            type = StickerType.IMAGE,
            title = "여름 바다",
            sourcePhotoId = photoIds.first(),
            imageKey = STICKER_IMAGE_KEY,
            textContent = null,
            layout = stickerLayout(10.0, 0),
            photoIds = photoIds.take(2),
            comments = listOf(RecapCommentCreation("함께한 여름", false, null, null)),
        ),
        AnalysisStickerResult(
            type = StickerType.TEXT,
            title = "여행 메모",
            sourcePhotoId = null,
            imageKey = null,
            textContent = "즐거웠던 순간",
            layout = stickerLayout(20.0, 1),
            photoIds = photoIds.drop(2).take(1),
            comments = emptyList(),
        ),
    )

private fun stickerLayout(
    posX: Double,
    zIndex: Int,
): StickerLayout =
    StickerLayout(
        posX = posX,
        posY = 5.0,
        scale = 1.0,
        rotation = 0.0,
        zIndex = zIndex,
        badgeOffsetX = 0.0,
        badgeOffsetY = 0.0,
        badgeRotation = 0.0,
    )
