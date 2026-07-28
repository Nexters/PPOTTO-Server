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
    analysisService: AnalysisService,
) : IntegrationTest({
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
                                        "photos": [
                                            {"takenAt": "2026-07-01T00:00:00Z", "contentType": "image/jpeg"},
                                            {"takenAt": "2026-07-02T00:00:00Z"}
                                        ]
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.analysisId").exists())
                        .andExpect(jsonPath("$.data.uploads.length()").value(2))
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

            When("지원하지 않는 contentType으로 요청하면") {
                Then("400 응답을 반환한다") {
                    mockMvc
                        .perform(
                            post("/analysis")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${board.id}",
                                        "photos": [{"takenAt": "2026-07-01T00:00:00Z", "contentType": "image/gif"}]
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("업로드 완료를 통보하면") {
                val created =
                    analysisService.createAnalysis(
                        board.id,
                        listOf(PhotoUploadItemRequest(Instant.now(), "image/jpeg")),
                    )

                Then("성공 응답에 업로드/실패 카운트가 담긴다") {
                    mockMvc
                        .perform(post("/analysis/${created.analysisId}/start"))
                        .andExpect(status().isOk)
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
                                    """{"boardId": "${UUID.randomUUID()}", "photos": [{"takenAt": "2026-07-01T00:00:00Z"}]}""",
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
