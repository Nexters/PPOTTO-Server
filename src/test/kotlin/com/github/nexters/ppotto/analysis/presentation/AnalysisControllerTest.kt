package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
@Import(AnalysisTestConfig::class)
class AnalysisControllerTest(
    @Autowired val mockMvc: MockMvc,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    analysisService: AnalysisService
) : IntegrationTest({
        fun createPhotosJson(count: Int): String {
            val photos =
                (0 until count).joinToString(",") {
                    """{"takenAt": "2026-07-0${(it % 9) + 1}T00:00:00Z", "contentType": "image/jpeg"}"""
                }
            return "[$photos]"
        }
        Given("Board가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)

            When("사진 목록을 담아 분석 생성을 요청하면") {
                Then("성공 응답에 analysisId와 사진별 signed URL이 담긴다") {
                    mockMvc
                        .perform(
                            post("/analysis")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${board.id}",
                                        "photos": ${createPhotosJson(90)}
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.analysisId").exists())
                        .andExpect(jsonPath("$.data.uploads.length()").value(90))
                        .andExpect(jsonPath("$.data.uploads[0].photoId").exists())
                        .andExpect(jsonPath("$.data.uploads[0].uploadUrl").exists())
                }
            }

            When("빈 사진 배열로 요청하면") {
                Then("400 응답을 반환한다") {
                    mockMvc
                        .perform(
                            post("/analysis")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"boardId": "${board.id}", "photos": []}"""),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("사진이 89장으로(하한 미만) 요청하면") {
                Then("400 응답과 ANALYSIS-001을 반환한다") {
                    mockMvc
                        .perform(
                            post("/analysis")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${board.id}",
                                        "photos": ${createPhotosJson(89)}
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-001"))
                }
            }

            When("사진이 101장으로(상한 초과) 요청하면") {
                Then("400 응답과 ANALYSIS-001을 반환한다") {
                    mockMvc
                        .perform(
                            post("/analysis")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${board.id}",
                                        "photos": ${createPhotosJson(101)}
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-001"))
                }
            }

            When("이미 활성 분석이 있는 상태에서 새 분석을 요청하면") {
                val existingBoard = boardRepository.save(userRepository.save().id)
                val existingPhotos = (0 until 90).map { PhotoUploadItemRequest(Instant.now(), "image/jpeg") }
                analysisService.createAnalysis(existingBoard.id, existingPhotos)

                Then("409 응답과 ANALYSIS-002을 반환한다") {
                    mockMvc
                        .perform(
                            post("/analysis")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${existingBoard.id}",
                                        "photos": ${createPhotosJson(90)}
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isConflict)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-002"))
                }
            }

            When("업로드 완료를 통보하면") {
                val uploadBoard = boardRepository.save(userRepository.save().id)
                val photos =
                    (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
                val created = analysisService.createAnalysis(uploadBoard.id, photos)

                Then("성공 응답에 업로드/실패 카운트가 담긴다") {
                    mockMvc
                        .perform(post("/analysis/${created.analysisId}/start"))
                        .andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.uploadedCount").exists())
                        .andExpect(jsonPath("$.data.failedCount").exists())
                        .andExpect(jsonPath("$.data.failedPhotoIds").exists())
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            When("분석 생성을 요청하면") {
                Then("404 응답을 반환한다") {
                    mockMvc
                        .perform(
                            post("/analysis")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${UUID.randomUUID()}",
                                        "photos": ${createPhotosJson(90)}
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isNotFound)
                }
            }
        }

        Given("존재하지 않는 analysisId로") {
            When("업로드 완료를 통보하면") {
                Then("404 응답을 반환한다") {
                    mockMvc
                        .perform(post("/analysis/${UUID.randomUUID()}/start"))
                        .andExpect(status().isNotFound)
                }
            }
        }
    })
