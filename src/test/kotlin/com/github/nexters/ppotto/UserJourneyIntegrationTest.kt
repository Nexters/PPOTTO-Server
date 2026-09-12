package com.github.nexters.ppotto

import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.analysis.support.FakePhotoStorage
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.jooq.tables.references.PHOTOS
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.jooq.tables.references.USERS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.UserJourneyTestConfig
import com.github.nexters.ppotto.user.application.WithdrawnUserCleanupService
import com.jayway.jsonpath.JsonPath
import io.kotest.core.spec.IsolationMode
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
import org.springframework.test.web.servlet.ResultActions
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

/**
 * 여정 spec은 단계가 앞 단계의 결과를 이어받으므로 `SingleInstance`로 한 번만 흐른다.
 * `InstancePerLeaf`에서는 leaf마다 조상 컨테이너만 다시 실행되고 앞선 형제 When은 건너뛰어, 이어달리기가 끊긴다.
 */
@AutoConfigureMockMvc
@Import(UserJourneyTestConfig::class, AnalysisTestConfig::class)
class UserJourneyIntegrationTest(
    mockMvc: MockMvc,
    cleanupService: WithdrawnUserCleanupService,
    dslContext: DSLContext,
    photoStorage: FakePhotoStorage,
    photoRepository: PhotoRepository,
) : IntegrationTest({
        Given("현재 필수 약관이 시행 중이고 가입한 적 없는 소셜 계정이 있을 때") {
            val termId =
                dslContext
                    .insertInto(TERMS, TERMS.CODE, TERMS.VERSION, TERMS.IS_REQUIRED, TERMS.CONTENT_URL, TERMS.EFFECTIVE_AT)
                    .values("JOURNEY-${UUID.randomUUID()}", "1.0", true, "https://example.com/terms", Instant.now().minusSeconds(60))
                    .returning(TERMS.ID)
                    .fetchOne(TERMS.ID)!!
            val providerUserId = "journey-${UUID.randomUUID()}"

            lateinit var accessToken: String
            lateinit var refreshToken: String
            lateinit var pendingTermIds: List<String>
            lateinit var userId: UUID
            lateinit var boardId: UUID
            lateinit var analysisId: UUID
            lateinit var secondAnalysisId: UUID
            lateinit var stickerId: UUID

            When("소셜 로그인을 호출하면") {
                val login =
                    mockMvc.perform(
                        post("/auth/login")
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"provider":"KAKAO","accessToken":"$providerUserId"}"""),
                    )
                val body = login.body()
                accessToken = JsonPath.read(body, "$.data.accessToken")
                refreshToken = JsonPath.read(body, "$.data.refreshToken")
                pendingTermIds = JsonPath.read(body, "$.data.pendingTerms[*].id")

                Then("신규 가입으로 처리하고 토큰을 발급한다") {
                    login
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.isNewUser").value(true))
                    accessToken.shouldStartWith("ey")
                }

                Then("아직 동의하지 않은 필수 약관을 함께 내려준다") {
                    login.andExpect(
                        jsonPath("$.data.pendingTerms[?(@.id == '$termId')].agreed").value(hasItem(false)),
                    )
                    pendingTermIds shouldContainExactly listOf(termId.toString())
                }
            }

            When("필수 약관을 빼고 동의를 제출하면") {
                val result =
                    mockMvc.perform(
                        post("/terms/agreements")
                            .authorized(accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"termIds":[]}"""),
                    )

                Then("TERM-001로 거절한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("TERM-001"))
                }
            }

            When("받은 약관 전부에 동의하면") {
                val agreement =
                    mockMvc.perform(
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
                    )

                Then("동의를 받아들인다") {
                    agreement.andExpect(status().isOk)
                }

                Then("약관 조회에서 동의 상태가 true로 바뀐다") {
                    mockMvc
                        .perform(get("/terms").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data[?(@.id == '$termId')].agreed").value(hasItem(true)))
                }
            }

            When("내 정보를 조회하면") {
                val me = mockMvc.perform(get("/users/me").authorized(accessToken))
                userId = UUID.fromString(JsonPath.read(me.body(), "$.data.id"))

                Then("카카오로 가입한 사용자를 돌려준다") {
                    me
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.provider").value("KAKAO"))
                }
            }

            When("보드 목록을 조회하고 보드를 하나 더 만들면") {
                val defaultBoards = mockMvc.perform(get("/boards").authorized(accessToken))
                val created =
                    mockMvc.perform(
                        post("/boards")
                            .authorized(accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"여행 보드"}"""),
                    )
                boardId = UUID.fromString(JsonPath.read(created.body(), "$.data.id"))

                Then("가입할 때 만들어진 기본 보드가 하나 있다") {
                    defaultBoards
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.length()").value(1))
                }

                Then("요청한 이름으로 보드가 생성된다") {
                    created
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.name").value("여행 보드"))
                }
            }

            When("분석을 생성하면") {
                val created =
                    mockMvc.perform(
                        post("/analysis")
                            .authorized(accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(boardId)),
                    )
                analysisId = UUID.fromString(JsonPath.read(created.body(), "$.data.analysisId"))

                Then("사진 수만큼 업로드 URL을 발급한다") {
                    created
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.uploads.length()").value(PHOTO_COUNT))
                }
            }

            When("사진을 모두 업로드하고 분석을 시작하면") {
                photoStorage.markUploaded(photoRepository.findAllByAnalysisId(AnalysisId(analysisId)))
                val started = mockMvc.perform(post("/analysis/$analysisId/start").authorized(accessToken))

                Then("업로드 검증을 통과하고 202로 받는다") {
                    started
                        .andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.data.uploadedCount").value(PHOTO_COUNT))
                        .andExpect(jsonPath("$.data.failedCount").value(0))
                }
            }

            When("보드를 다시 조회하면") {
                val board = mockMvc.perform(get("/boards/$boardId").authorized(accessToken))
                val stickerIds =
                    JsonPath
                        .read<List<String>>(board.body(), "$.data.stickers[*].id")
                        .map(UUID::fromString)
                stickerId = stickerIds.single()

                Then("파이프라인이 만든 스티커 한 장이 보드에 붙어 있다") {
                    stickerIds shouldHaveSize 1
                    board
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.stickers[0].type").value("IMAGE"))
                        .andExpect(jsonPath("$.data.stickers[0].title").value("테스트뱃지"))
                        .andExpect(jsonPath("$.data.stickers[0].isNew").value(true))
                }

                Then("아직 배치하지 않았으므로 좌표는 비어 있고 기본 배율만 있다") {
                    board
                        .andExpect(jsonPath("$.data.stickers[0].posX").doesNotExist())
                        .andExpect(jsonPath("$.data.stickers[0].posY").doesNotExist())
                        .andExpect(jsonPath("$.data.stickers[0].zIndex").doesNotExist())
                        .andExpect(jsonPath("$.data.stickers[0].scale").value(1.0))
                        .andExpect(jsonPath("$.data.stickers[0].rotation").value(0.0))
                }
            }

            When("리캡 상세를 조회하면") {
                val recap = mockMvc.perform(get("/stickers/$stickerId").authorized(accessToken))

                Then("한 줄 요약과 말풍선·키워드 댓글을 함께 돌려준다") {
                    recap
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.summary").value("테스트 리캡 문구입니다."))
                        .andExpect(jsonPath("$.data.comments.length()").value(2))
                        .andExpect(jsonPath("$.data.comments[0].content").value("테스트 말풍선"))
                        .andExpect(jsonPath("$.data.comments[0].posX").value(-96.0))
                        .andExpect(jsonPath("$.data.comments[0].posY").value(-150.0))
                        .andExpect(jsonPath("$.data.comments[1].content").value("테스트 키워드"))
                        .andExpect(jsonPath("$.data.comments[1].posX").doesNotExist())
                        .andExpect(jsonPath("$.data.comments[1].posY").doesNotExist())
                }

                Then("분석에 쓴 사진을 서명된 읽기 URL과 함께 돌려준다") {
                    recap.andExpect(jsonPath("$.data.photos.length()").value(PHOTO_COUNT))
                    JsonPath
                        .read<String>(recap.body(), "$.data.photos[0].imageUrl")
                        .shouldStartWith("https://storage.googleapis.com/ppotto-test-bucket/photos/$analysisId/")
                }
            }

            When("리캡을 열어 보고 스티커 제목을 바꾸면") {
                val viewed = mockMvc.perform(post("/stickers/$stickerId/view").authorized(accessToken))
                val renamed =
                    mockMvc.perform(
                        patch("/stickers/$stickerId")
                            .authorized(accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"title":"제주 여행"}"""),
                    )

                Then("조회 기록과 제목 변경이 모두 반영된다") {
                    viewed.andExpect(status().isOk)
                    renamed
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.title").value("제주 여행"))
                }

                Then("보드 조회에서도 바뀐 제목과 읽음 상태가 보인다") {
                    mockMvc
                        .perform(get("/boards/$boardId").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.stickers.length()").value(1))
                        .andExpect(jsonPath("$.data.stickers[0].title").value("제주 여행"))
                        .andExpect(jsonPath("$.data.stickers[0].isNew").value(false))
                }
            }

            When("스티커를 삭제하면") {
                val deleted = mockMvc.perform(delete("/stickers/$stickerId").authorized(accessToken))

                Then("보드에서 사라진다") {
                    deleted.andExpect(status().isOk)
                    mockMvc
                        .perform(get("/boards/$boardId").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.stickers.length()").value(0))
                }
            }

            When("두 번째 분석을 끝까지 돌리면") {
                val created =
                    mockMvc.perform(
                        post("/analysis")
                            .authorized(accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(boardId)),
                    )
                secondAnalysisId = UUID.fromString(JsonPath.read(created.body(), "$.data.analysisId"))
                photoStorage.markUploaded(photoRepository.findAllByAnalysisId(AnalysisId(secondAnalysisId)))
                val started = mockMvc.perform(post("/analysis/$secondAnalysisId/start").authorized(accessToken))

                Then("파이프라인이 동기로 끝나 보드에 스티커가 다시 한 장 생긴다") {
                    created.andExpect(status().isOk)
                    started.andExpect(status().isAccepted)
                    mockMvc
                        .perform(get("/boards/$boardId").authorized(accessToken))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.stickers.length()").value(1))
                }
            }

            When("다른 사용자가 로그인해 자기 보드를 갖고 있으면") {
                val otherLogin =
                    mockMvc.perform(
                        post("/auth/login")
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"provider":"KAKAO","accessToken":"other-$providerUserId"}"""),
                    )
                val otherAccessToken = JsonPath.read<String>(otherLogin.body(), "$.data.accessToken")

                Then("남의 보드는 존재 자체를 숨기고 BOARD-002로 404를 준다") {
                    mockMvc
                        .perform(get("/boards/$boardId").authorized(otherAccessToken))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                }

                Then("남의 분석 상태도 ANALYSIS-005로 404를 준다") {
                    mockMvc
                        .perform(get("/analysis/$secondAnalysisId").authorized(otherAccessToken))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }

            When("서명이 깨진 토큰으로 보호된 API를 호출하면") {
                val tampered = accessToken.substringBeforeLast('.') + ".tampered-signature"
                val result = mockMvc.perform(get("/boards").authorized(tampered))

                Then("Bearer 필터가 COMMON-004로 401을 준다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }

            When("토큰 없이 보호된 API를 호출하면") {
                val result = mockMvc.perform(get("/boards").header("X-API-Version", "1"))

                Then("COMMON-004로 401을 준다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }

            When("탈퇴하면") {
                val withdrawal = mockMvc.perform(delete("/users/me").authorized(accessToken))

                Then("탈퇴를 받아들이고 내 정보는 USER-001로 사라진다") {
                    withdrawal.andExpect(status().isOk)
                    mockMvc
                        .perform(get("/users/me").authorized(accessToken))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("USER-001"))
                }

                Then("리프레시 토큰도 AUTH-002로 끊긴다") {
                    mockMvc
                        .perform(
                            post("/auth/refresh")
                                .header("X-API-Version", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"refreshToken":"$refreshToken"}"""),
                        ).andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("AUTH-002"))
                }

                Then("데이터는 아직 남아 있다") {
                    dslContext.fetchExists(STICKERS, STICKERS.BOARD_ID.eq(BoardId(boardId))) shouldBe true
                }
            }

            When("정리 배치를 돌리면") {
                val result = cleanupService.cleanup(deletedBefore = Instant.now().plusSeconds(1), batchSize = 10)

                Then("탈퇴한 사용자만 정리 대상으로 잡는다") {
                    result.deletedUserIds shouldContainExactly listOf(UserId(userId))
                }

                Then("스티커·사진·분석·보드·약관 동의·사용자가 모두 사라진다") {
                    dslContext.fetchExists(STICKERS, STICKERS.BOARD_ID.eq(BoardId(boardId))) shouldBe false
                    dslContext.fetchExists(PHOTOS, PHOTOS.ANALYSIS_ID.eq(AnalysisId(analysisId))) shouldBe false
                    dslContext.fetchExists(PHOTOS, PHOTOS.ANALYSIS_ID.eq(AnalysisId(secondAnalysisId))) shouldBe false
                    dslContext.fetchExists(ANALYSIS, ANALYSIS.USER_ID.eq(UserId(userId))) shouldBe false
                    dslContext.fetchExists(BOARDS, BOARDS.USER_ID.eq(UserId(userId))) shouldBe false
                    dslContext.fetchExists(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID.eq(UserId(userId))) shouldBe false
                    dslContext.fetchExists(USERS, USERS.ID.eq(UserId(userId))) shouldBe false
                }
            }
        }
    }) {
    override fun isolationMode(): IsolationMode = IsolationMode.SingleInstance
}

private fun ResultActions.body(): String =
    andReturn()
        .response
        .getContentAsString(Charsets.UTF_8)

private fun MockHttpServletRequestBuilder.authorized(accessToken: String): MockHttpServletRequestBuilder =
    header("Authorization", "Bearer $accessToken")
        .header("X-API-Version", "1")

private fun createAnalysisBody(boardId: UUID): String =
    (0 until PHOTO_COUNT)
        .joinToString(",") {
            """{"items":[{"takenAt":"${Instant.parse("2026-07-01T00:00:00Z").plusSeconds(it.toLong())}",""" +
                """"contentType":"image/jpeg","isRepresentative":true}]}"""
        }.let { """{"boardId":"$boardId","photos":[$it]}""" }
