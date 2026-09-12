package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.imageStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import com.jayway.jsonpath.JsonPath
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.containsString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
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

@AutoConfigureMockMvc
class StickerControllerTest(
    @Autowired val mockMvc: MockMvc,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        fun MockHttpServletRequestBuilder.authenticatedAs(userId: UUID): MockHttpServletRequestBuilder =
            with(authentication(UsernamePasswordAuthenticationToken.authenticated(userId, null, emptyList())))

        Given("사용자 보드에 이미지 스티커와 리캡이 등록된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photo =
                photoRepository
                    .saveAll(
                        analysis.id,
                        board.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            photoRepository.markCompletedBatch(mapOf(photo.id to Instant.now()))
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    imageStickerCreation(
                        sourcePhotoId = photo.id,
                        imageKey = "stickers/controller.png",
                        summary = "웃기고 귀여우면 일단 주워요",
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, listOf(photo.id))
            val comments =
                stickerRecapRepository.saveComments(
                    sticker.id,
                    listOf(
                        RecapCommentCreation("말풍선", 3.0, 4.0),
                        RecapCommentCreation("키워드", null, null),
                    ),
                )
            val bubbleComment = comments.first { it.content == "말풍선" }
            val chipComment = comments.first { it.content == "키워드" }

            When("리캡 상세를 요청하면") {
                val result = mockMvc.perform(get("/stickers/${sticker.id}").authenticatedAs(board.userId.value))

                Then("스티커와 한 줄 요약을 응답한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.sticker.id").value(sticker.id.toString()))
                        .andExpect(jsonPath("$.data.sticker.mainColor").value("#FF6B6B"))
                        .andExpect(jsonPath("$.data.summary").value("웃기고 귀여우면 일단 주워요"))
                }

                Then("말풍선은 좌표를 갖고 키워드 칩은 좌표가 없다") {
                    result
                        .andExpect(jsonPath("$.data.comments[0].content").value("말풍선"))
                        .andExpect(jsonPath("$.data.comments[0].posX").value(3.0))
                        .andExpect(jsonPath("$.data.comments[0].posY").value(4.0))
                        .andExpect(jsonPath("$.data.comments[1].content").value("키워드"))
                        .andExpect(jsonPath("$.data.comments[1].posX").doesNotExist())
                        .andExpect(jsonPath("$.data.comments[1].posY").doesNotExist())
                }

                Then("배치를 정하지 않은 스티커는 좌표와 겹침 순서를 내려주지 않는다") {
                    result
                        .andExpect(jsonPath("$.data.sticker.posX").doesNotExist())
                        .andExpect(jsonPath("$.data.sticker.posY").doesNotExist())
                        .andExpect(jsonPath("$.data.sticker.zIndex").doesNotExist())
                        .andExpect(jsonPath("$.data.sticker.scale").value(1.0))
                        .andExpect(jsonPath("$.data.sticker.rotation").value(0.0))
                }

                Then("리캡 사진은 읽기용 signed URL로 내려준다") {
                    result
                        .andExpect(jsonPath("$.data.photos[0].id").value(photo.id.toString()))
                        .andExpect(
                            jsonPath("$.data.photos[0].imageUrl")
                                .value(containsString("photos/${analysis.id}/${photo.id}.jpg")),
                        )
                }
            }

            When("제목을 수정하면") {
                val result =
                    mockMvc.perform(
                        patch("/stickers/${sticker.id}")
                            .authenticatedAs(board.userId.value)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"title":"새 제목"}"""),
                    )

                Then("변경한 제목을 응답한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.id").value(sticker.id.toString()))
                        .andExpect(jsonPath("$.data.title").value("새 제목"))
                }
            }

            When("빈 제목으로 수정하면") {
                val result =
                    mockMvc.perform(
                        patch("/stickers/${sticker.id}")
                            .authenticatedAs(board.userId.value)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"title":" "}"""),
                    )

                Then("COMMON-001 오류를 응답한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("리캡을 두 번 열람 처리하면") {
                mockMvc.perform(post("/stickers/${sticker.id}/view").authenticatedAs(board.userId.value))
                val firstViewedAt = checkNotNull(stickerRepository.findById(sticker.id)?.viewedAt)
                val result = mockMvc.perform(post("/stickers/${sticker.id}/view").authenticatedAs(board.userId.value))

                Then("두 번째 호출도 성공한다") {
                    result.andExpect(status().isOk)
                }

                Then("최초 열람 시각을 덮어쓰지 않는다") {
                    stickerRepository.findById(sticker.id)?.viewedAt shouldBe firstViewedAt
                }

                Then("이후 리캡 조회에서 isNew가 false로 뒤집힌다") {
                    mockMvc
                        .perform(get("/stickers/${sticker.id}").authenticatedAs(board.userId.value))
                        .andExpect(jsonPath("$.data.sticker.isNew").value(false))
                }
            }

            When("다른 사용자가 리캡을 조회하면") {
                val otherUser = userRepository.saveTestUser()
                val result = mockMvc.perform(get("/stickers/${sticker.id}").authenticatedAs(otherUser.id.value))

                Then("STICKER-001 404를 응답한다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("STICKER-001"))
                }
            }

            When("말풍선 코멘트 위치를 수정하면") {
                val result =
                    mockMvc.perform(
                        patch("/stickers/${sticker.id}/comments")
                            .authenticatedAs(board.userId.value)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"comments":[{"id":"${bubbleComment.id}","posX":10.5,"posY":-20.5}]}"""),
                    )

                Then("성공을 응답한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                }

                Then("바뀐 위치가 리캡 상세에 반영된다") {
                    mockMvc
                        .perform(get("/stickers/${sticker.id}").authenticatedAs(board.userId.value))
                        .andExpect(jsonPath("$.data.comments[0].posX").value(10.5))
                        .andExpect(jsonPath("$.data.comments[0].posY").value(-20.5))
                }
            }

            When("키워드 칩 코멘트의 위치를 수정하려 하면") {
                val result =
                    mockMvc.perform(
                        patch("/stickers/${sticker.id}/comments")
                            .authenticatedAs(board.userId.value)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"comments":[{"id":"${chipComment.id}","posX":1.0,"posY":2.0}]}"""),
                    )

                Then("STICKER-004 오류를 응답한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("STICKER-004"))
                }
            }

            When("존재하지 않는 코멘트 id로 위치를 수정하려 하면") {
                val result =
                    mockMvc.perform(
                        patch("/stickers/${sticker.id}/comments")
                            .authenticatedAs(board.userId.value)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"comments":[{"id":"${UUID.randomUUID()}","posX":1.0,"posY":2.0}]}"""),
                    )

                Then("STICKER-004 오류를 응답한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("STICKER-004"))
                }
            }

            When("다른 사용자가 코멘트 위치를 수정하려 하면") {
                val otherUser = userRepository.saveTestUser()
                val result =
                    mockMvc.perform(
                        patch("/stickers/${sticker.id}/comments")
                            .authenticatedAs(otherUser.id.value)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"comments":[{"id":"${bubbleComment.id}","posX":1.0,"posY":2.0}]}"""),
                    )

                Then("STICKER-001 오류로 소유권을 숨긴다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("STICKER-001"))
                }
            }

            When("스티커를 삭제하면") {
                val result = mockMvc.perform(delete("/stickers/${sticker.id}").authenticatedAs(board.userId.value))

                Then("성공을 응답한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                }

                Then("이후 리캡 조회는 STICKER-001 404다") {
                    mockMvc
                        .perform(get("/stickers/${sticker.id}").authenticatedAs(board.userId.value))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("STICKER-001"))
                }

                Then("활성 스티커에서도 사라진다") {
                    stickerRepository.findById(sticker.id).shouldBeNull()
                }
            }

            When("인증 없이 리캡을 조회하면") {
                val result = mockMvc.perform(get("/stickers/${sticker.id}"))

                Then("COMMON-004 오류를 응답한다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }

            When("사진 없이 공유한 뒤 인증 없이 공유 링크로 조회하면") {
                val shareToken =
                    mockMvc
                        .perform(
                            post("/stickers/${sticker.id}/share")
                                .authenticatedAs(board.userId.value)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"includePhotos":false}"""),
                        ).andExpect(status().isOk)
                        .andReturn()
                        .response
                        .contentAsString
                        .let { JsonPath.read<String>(it, "$.data.shareToken") }

                Then("리캡 내용을 응답하되 사진은 비어 있다") {
                    mockMvc
                        .perform(get("/stickers/shared/$shareToken"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.sticker.id").value(sticker.id.toString()))
                        .andExpect(jsonPath("$.data.sticker.isNew").value(false))
                        .andExpect(jsonPath("$.data.comments[0].content").value("말풍선"))
                        .andExpect(jsonPath("$.data.photos").isEmpty)
                }

                Then("공유를 해제하면 같은 토큰은 STICKER-001 404가 된다") {
                    mockMvc
                        .perform(delete("/stickers/${sticker.id}/share").authenticatedAs(board.userId.value))
                        .andExpect(status().isOk)
                    mockMvc
                        .perform(get("/stickers/shared/$shareToken"))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("STICKER-001"))
                }
            }

            When("인증 없이 리캡을 열람 처리하면") {
                val result = mockMvc.perform(post("/stickers/${sticker.id}/view"))

                Then("COMMON-004 오류를 응답한다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })
