package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.FakeRecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.support.StickerTestConfig
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
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
@Import(StickerTestConfig::class)
class StickerControllerTest(
    @Autowired val mockMvc: MockMvc,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
    fakePhotoPort: FakeRecapPhotoQueryPort,
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
            val board = boardRepository.save(userRepository.save().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photo =
                photoRepository
                    .saveAll(
                        analysis.id,
                        board.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    StickerCreation(
                        type = StickerType.IMAGE,
                        title = "원래 제목",
                        sourcePhotoId = photo.id,
                        imageKey = "stickers/controller.png",
                        textContent = null,
                        layout = controllerLayout(),
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, listOf(photo.id))
            stickerRecapRepository.saveComments(
                sticker.id,
                listOf(RecapCommentCreation("코멘트", false, null, null)),
            )
            fakePhotoPort.photos[photo.id] =
                RecapPhotoMetadata(
                    photo.id,
                    "https://example.com/photos/${photo.id}",
                    requireNotNull(photo.takenAt),
                )

            When("리캡 상세를 요청하면") {
                authenticate(board.userId)

                Then("스티커와 코멘트와 사진을 응답한다") {
                    mockMvc
                        .perform(get("/stickers/${sticker.id}"))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.sticker.id").value(sticker.id.toString()))
                        .andExpect(jsonPath("$.data.comments[0].content").value("코멘트"))
                        .andExpect(jsonPath("$.data.photos[0].id").value(photo.id.toString()))
                }
            }

            When("제목을 수정하면") {
                authenticate(board.userId)

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
                authenticate(board.userId)

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
                authenticate(board.userId)

                Then("여러 번 호출해도 성공한다") {
                    mockMvc.perform(post("/stickers/${sticker.id}/view")).andExpect(status().isOk)
                    mockMvc.perform(post("/stickers/${sticker.id}/view")).andExpect(status().isOk)
                }
            }

            When("다른 사용자가 리캡을 조회하면") {
                authenticate(userRepository.save().id)

                Then("404 응답을 반환한다") {
                    mockMvc
                        .perform(get("/stickers/${sticker.id}"))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("STICKER-001"))
                }
            }

            When("스티커를 삭제하면") {
                authenticate(board.userId)

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

private fun controllerLayout() =
    StickerLayout(
        posX = 1.0,
        posY = 2.0,
        scale = 1.0,
        rotation = 0.0,
        zIndex = 0,
        badgeOffsetX = 0.0,
        badgeOffsetY = 0.0,
        badgeRotation = 0.0,
    )
