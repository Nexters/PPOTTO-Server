package com.github.nexters.ppotto.sticker.presentation

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.board.application.BoardShareService
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.imageStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.hamcrest.Matchers.hasSize
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@AutoConfigureMockMvc
class SharedBoardRecapControllerTest(
    mockMvc: MockMvc,
    boardShareService: BoardShareService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    analysisRepository: AnalysisRepository,
    photoRepository: PhotoRepository,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
) : IntegrationTest({
        Given("리캡이 있는 스티커가 올라간 보드에서") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "제주 여행")
            val analysis = analysisRepository.save(owner.id, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    imageStickerCreation(
                        sourcePhotoId = photos.first().id,
                        imageKey = "stickers/recap.png",
                        title = "리캡",
                        summary = "웃기고 귀여우면 일단 주워요",
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, photos.map { it.id })

            When("로그인하지 않은 사용자가 리캡까지 공유된 링크로 조회하면") {
                val share = boardShareService.share(board.id, owner.id, BoardShareScope.RECAP)
                val result = mockMvc.perform(get("/boards/shared/${share.shareToken}/stickers/${sticker.id}"))

                Then("사진을 뺀 리캡을 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.sticker.id").value(sticker.id.toString()))
                        .andExpect(jsonPath("$.data.summary").value("웃기고 귀여우면 일단 주워요"))
                        .andExpect(jsonPath("$.data.photos", hasSize<Any>(0)))
                }
            }

            When("테마 속 사진까지 공유된 링크로 최신 API 버전으로 조회하면") {
                val share = boardShareService.share(board.id, owner.id, BoardShareScope.PHOTOS)
                val result =
                    mockMvc.perform(
                        get("/boards/shared/${share.shareToken}/stickers/${sticker.id}").header("X-API-Version", "2"),
                    )

                Then("사진까지 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.photos", hasSize<Any>(1)))
                }
            }

            When("보드만 공유된 링크로 조회하면") {
                val share = boardShareService.share(board.id, owner.id, BoardShareScope.BOARD)
                val result = mockMvc.perform(get("/boards/shared/${share.shareToken}/stickers/${sticker.id}"))

                Then("공유 범위 밖이라 BOARD-011을 반환한다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-011"))
                }
            }

            When("발급된 적 없는 토큰으로 조회하면") {
                val result = mockMvc.perform(get("/boards/shared/unknown-token/stickers/${sticker.id}"))

                Then("BOARD-010을 반환한다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-010"))
                }
            }
        }
    })
