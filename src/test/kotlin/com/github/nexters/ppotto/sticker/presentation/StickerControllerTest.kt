package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerCreation
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.hamcrest.Matchers.containsString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc(addFilters = false)
class StickerControllerTest(
    @Autowired val mockMvc: MockMvc,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        fun authenticate(userId: UUID) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null)
        }

        Given("사용자 보드에 이미지 스티커와 리캡이 등록된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId.value, board.id.value)
            val photo =
                photoRepository
                    .saveAll(
                        analysis.id,
                        board.id.value,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            photoRepository.markCompletedBatch(mapOf(photo.id to Instant.now()))
            val sticker =
                stickerRepository.save(
                    AnalysisId(analysis.id),
                    board.id,
                    StickerCreation(
                        type = StickerType.IMAGE,
                        title = "원래 제목",
                        summary = "웃기고 귀여우면 일단 주워요",
                        sourcePhotoId = PhotoId(photo.id),
                        imageKey = "stickers/controller.png",
                        textContent = null,
                        mainColor = "#FF6B6B",
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, listOf(PhotoId(photo.id)))
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
                authenticate(board.userId.value)

                Then("스티커와 한 줄 요약과 코멘트와 사진을 응답한다") {
                    mockMvc
                        .perform(get("/stickers/${sticker.id}"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.sticker.id").value(sticker.id.toString()))
                        .andExpect(jsonPath("$.data.sticker.mainColor").value("#FF6B6B"))
                        .andExpect(jsonPath("$.data.summary").value("웃기고 귀여우면 일단 주워요"))
                        .andExpect(jsonPath("$.data.comments[0].content").value("말풍선"))
                        .andExpect(jsonPath("$.data.comments[0].posX").value(3.0))
                        .andExpect(jsonPath("$.data.comments[0].posY").value(4.0))
                        .andExpect(jsonPath("$.data.comments[1].content").value("키워드"))
                        .andExpect(jsonPath("$.data.comments[1].posX").doesNotExist())
                        .andExpect(jsonPath("$.data.comments[1].posY").doesNotExist())
                        .andExpect(jsonPath("$.data.sticker.posX").doesNotExist())
                        .andExpect(jsonPath("$.data.sticker.posY").doesNotExist())
                        .andExpect(jsonPath("$.data.sticker.zIndex").doesNotExist())
                        .andExpect(jsonPath("$.data.sticker.scale").value(1.0))
                        .andExpect(jsonPath("$.data.sticker.rotation").value(0.0))
                        .andExpect(jsonPath("$.data.photos[0].id").value(photo.id.toString()))
                        .andExpect(
                            jsonPath("$.data.photos[0].imageUrl")
                                .value(containsString("photos/${analysis.id}/${photo.id}.jpg")),
                        )
                }
            }

            When("제목을 수정하면") {
                authenticate(board.userId.value)

                Then("변경한 제목을 응답한다") {
                    mockMvc
                        .perform(
                            patch("/stickers/${sticker.id}")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"title":"새 제목"}"""),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.id").value(sticker.id.toString()))
                        .andExpect(jsonPath("$.data.title").value("새 제목"))
                }
            }

            When("빈 제목으로 수정하면") {
                authenticate(board.userId.value)

                Then("400 응답을 반환한다") {
                    mockMvc
                        .perform(
                            patch("/stickers/${sticker.id}")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"title":" "}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("리캡을 열람 처리하면") {
                authenticate(board.userId.value)

                Then("여러 번 호출해도 성공한다") {
                    mockMvc.perform(post("/stickers/${sticker.id}/view")).andExpect(status().isOk)
                    mockMvc.perform(post("/stickers/${sticker.id}/view")).andExpect(status().isOk)
                }
            }

            When("다른 사용자가 리캡을 조회하면") {
                val otherUser = userRepository.saveTestUser()
                authenticate(otherUser.id.value)

                Then("404 응답을 반환한다") {
                    mockMvc
                        .perform(get("/stickers/${sticker.id}"))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("STICKER-001"))
                }
            }

            When("말풍선 코멘트 위치를 수정하면") {
                authenticate(board.userId.value)

                Then("바뀐 위치를 저장한다") {
                    mockMvc
                        .perform(
                            patch("/stickers/${sticker.id}/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"comments":[{"id":"${bubbleComment.id}","posX":10.5,"posY":-20.5}]}"""),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))

                    mockMvc
                        .perform(get("/stickers/${sticker.id}"))
                        .andExpect(jsonPath("$.data.comments[0].posX").value(10.5))
                        .andExpect(jsonPath("$.data.comments[0].posY").value(-20.5))
                }
            }

            When("키워드 칩 코멘트의 위치를 수정하려 하면") {
                authenticate(board.userId.value)

                Then("400 응답을 반환한다") {
                    mockMvc
                        .perform(
                            patch("/stickers/${sticker.id}/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"comments":[{"id":"${chipComment.id}","posX":1.0,"posY":2.0}]}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("존재하지 않는 코멘트 id로 위치를 수정하려 하면") {
                authenticate(board.userId.value)

                Then("400 응답을 반환한다") {
                    mockMvc
                        .perform(
                            patch("/stickers/${sticker.id}/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"comments":[{"id":"${UUID.randomUUID()}","posX":1.0,"posY":2.0}]}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("다른 사용자가 코멘트 위치를 수정하려 하면") {
                val otherUser = userRepository.saveTestUser()
                authenticate(otherUser.id.value)

                Then("404 응답을 반환한다") {
                    mockMvc
                        .perform(
                            patch("/stickers/${sticker.id}/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"comments":[{"id":"${bubbleComment.id}","posX":1.0,"posY":2.0}]}"""),
                        ).andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("STICKER-001"))
                }
            }

            When("스티커를 삭제하면") {
                authenticate(board.userId.value)

                Then("성공 응답을 반환한다") {
                    mockMvc
                        .perform(delete("/stickers/${sticker.id}"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                }
            }

            When("인증 없이 리캡을 조회하면") {
                SecurityContextHolder.clearContext()

                Then("401 응답을 반환한다") {
                    mockMvc
                        .perform(get("/stickers/${sticker.id}"))
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })
