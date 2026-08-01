package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.application.PhotoUploadItemRequest
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
    analysisRepository: AnalysisRepository,
    analysisService: AnalysisService,
    dslContext: DSLContext,
) : IntegrationTest({
        fun createPhotosJson(count: Int): String {
            val photos =
                (0 until count).joinToString(",") {
                    """{"takenAt": "2026-07-0${(it % 9) + 1}T00:00:00Z", "contentType": "image/jpeg"}"""
                }
            return "[$photos]"
        }

        fun authenticatedPost(
            url: String,
            userId: UUID,
        ): MockHttpServletRequestBuilder =
            post(url)
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken.authenticated(userId, null, emptyList()),
                    ),
                )

        fun authenticatedGet(
            url: String,
            userId: UUID,
        ): MockHttpServletRequestBuilder =
            get(url)
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken.authenticated(userId, null, emptyList()),
                    ),
                )

        Given("Board가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)

            When("사진 목록을 담아 분석 생성을 요청하면") {
                Then("성공 응답에 analysisId와 사진별 signed URL이 담긴다") {
                    mockMvc
                        .perform(
                            authenticatedPost("/analysis", board.userId.value)
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
                            authenticatedPost("/analysis", board.userId.value)
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
                            authenticatedPost("/analysis", board.userId.value)
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
                            authenticatedPost("/analysis", board.userId.value)
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
                val existingPhotos = (0 until 90).map { PhotoUploadItemRequest(Instant.now(), "image/jpeg") }
                analysisService.createAnalysis(board.userId.value, board.id.value, existingPhotos)

                Then("409 응답과 ANALYSIS-002을 반환한다") {
                    mockMvc
                        .perform(
                            authenticatedPost("/analysis", board.userId.value)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${board.id}",
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
                val uploadBoard = boardRepository.save(userRepository.saveTestUser().id)
                val photos =
                    (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
                val created = analysisService.createAnalysis(uploadBoard.userId.value, uploadBoard.id.value, photos)

                Then("성공 응답에 업로드/실패 카운트가 담긴다") {
                    mockMvc
                        .perform(authenticatedPost("/analysis/${created.analysisId}/start", uploadBoard.userId.value))
                        .andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.uploadedCount").exists())
                        .andExpect(jsonPath("$.data.failedCount").exists())
                        .andExpect(jsonPath("$.data.failedPhotoIds").exists())
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            val userId = userRepository.saveTestUser().rawId

            When("분석 생성을 요청하면") {
                Then("404 응답과 BOARD-002를 반환한다") {
                    mockMvc
                        .perform(
                            authenticatedPost("/analysis", userId)
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
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                }
            }
        }

        Given("존재하지 않는 analysisId로") {
            val userId = userRepository.saveTestUser().rawId

            When("업로드 완료를 통보하면") {
                Then("404 응답을 반환한다") {
                    mockMvc
                        .perform(authenticatedPost("/analysis/${UUID.randomUUID()}/start", userId))
                        .andExpect(status().isNotFound)
                }
            }
        }

        Given("활성 분석이 없는 사용자로") {
            val userId = userRepository.saveTestUser().rawId

            When("진행 중 분석을 조회하면") {
                Then("200 응답과 null data를 반환한다") {
                    mockMvc
                        .perform(authenticatedGet("/analysis/active", userId))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }
            }
        }

        Given("UPLOADING 상태의 분석이 있으면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId.value, board.id.value)

            When("진행 중 분석을 조회하면") {
                Then("분석 상태를 반환한다") {
                    mockMvc
                        .perform(authenticatedGet("/analysis/active", board.userId.value))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.id").value(analysis.id.toString()))
                        .andExpect(jsonPath("$.data.boardId").value(board.id.toString()))
                        .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                        .andExpect(jsonPath("$.data.progress").value(0))
                        .andExpect(jsonPath("$.data.failedReason").doesNotExist())
                        .andExpect(jsonPath("$.data.startedAt").doesNotExist())
                        .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                }
            }
        }

        Given("ANALYZING 상태의 분석이 있으면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId.value, board.id.value)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .set(ANALYSIS.PROGRESS, 10)
                .set(ANALYSIS.STARTED_AT, Instant.parse("2026-07-27T05:02:11Z"))
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            When("분석 상태를 조회하면") {
                Then("로딩 화면 폴링용 상태를 반환한다") {
                    mockMvc
                        .perform(authenticatedGet("/analysis/${analysis.id}", board.userId.value))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.id").value(analysis.id.toString()))
                        .andExpect(jsonPath("$.data.boardId").value(board.id.toString()))
                        .andExpect(jsonPath("$.data.status").value("ANALYZING"))
                        .andExpect(jsonPath("$.data.progress").value(10))
                        .andExpect(jsonPath("$.data.startedAt").exists())
                        .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                }
            }
        }

        Given("COMPLETED 상태의 분석이 있으면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId.value, board.id.value)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
                .set(ANALYSIS.PROGRESS, 100)
                .set(ANALYSIS.COMPLETED_AT, Instant.parse("2026-07-27T05:03:38Z"))
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            When("진행 중 분석을 조회하면") {
                Then("active에는 포함하지 않는다") {
                    mockMvc
                        .perform(authenticatedGet("/analysis/active", board.userId.value))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }
            }

            When("분석 상태를 조회하면") {
                Then("완료 상태를 반환한다") {
                    mockMvc
                        .perform(authenticatedGet("/analysis/${analysis.id}", board.userId.value))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.data.progress").value(100))
                        .andExpect(jsonPath("$.data.completedAt").exists())
                }
            }
        }

        Given("다른 사용자의 analysisId로") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(ownerBoard.userId.value, ownerBoard.id.value)
            val otherUserId = userRepository.saveTestUser().rawId

            When("분석 상태를 조회하면") {
                Then("404 응답과 ANALYSIS-005를 반환한다") {
                    mockMvc
                        .perform(authenticatedGet("/analysis/${analysis.id}", otherUserId))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }
        }

        Given("인증되지 않은 요청으로") {
            When("진행 중 분석을 조회하면") {
                Then("401 응답과 COMMON-004를 반환한다") {
                    mockMvc
                        .perform(get("/analysis/active"))
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }

        Given("인증되지 않은 요청으로") {
            val board = boardRepository.save(userRepository.saveTestUser().id)

            When("분석 생성을 요청하면") {
                Then("401 응답과 COMMON-004를 반환한다") {
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
                        ).andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }

        Given("다른 사용자의 Board가 등록된 상태에서") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val otherUserId = userRepository.saveTestUser().rawId

            When("분석 생성을 요청하면") {
                Then("404 응답과 BOARD-002를 반환한다") {
                    mockMvc
                        .perform(
                            authenticatedPost("/analysis", otherUserId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${ownerBoard.id}",
                                        "photos": ${createPhotosJson(90)}
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                }
            }
        }

        Given("다른 사용자의 analysisId로") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val photos =
                (0 until 90).map { i -> PhotoUploadItemRequest(Instant.now().plusSeconds(i.toLong()), "image/jpeg") }
            val created = analysisService.createAnalysis(ownerBoard.userId.value, ownerBoard.id.value, photos)
            val otherUserId = userRepository.saveTestUser().rawId

            When("업로드 완료를 통보하면") {
                Then("404 응답을 반환한다") {
                    mockMvc
                        .perform(authenticatedPost("/analysis/${created.analysisId}/start", otherUserId))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }
        }

        Given("Board가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)

            When("contentType 필드를 누락하고 요청하면") {
                Then("400 응답을 반환한다") {
                    val photosJson =
                        (0 until 90).joinToString(",") {
                            """{"takenAt": "2026-07-0${(it % 9) + 1}T00:00:00Z"}"""
                        }
                    mockMvc
                        .perform(
                            authenticatedPost("/analysis", board.userId.value)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${board.id}",
                                        "photos": [$photosJson]
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("contentType이 null이고 요청하면") {
                Then("400 응답을 반환한다") {
                    mockMvc
                        .perform(
                            authenticatedPost("/analysis", board.userId.value)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${board.id}",
                                        "photos": [{"takenAt": "2026-07-01T00:00:00Z", "contentType": null}]
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("지원하지 않는 contentType(image/gif)으로 요청하면") {
                Then("400 응답을 반환한다") {
                    val photosJson =
                        (0 until 90).joinToString(",") {
                            if (it == 0) {
                                """{"takenAt": "2026-07-01T00:00:00Z", "contentType": "image/gif"}"""
                            } else {
                                """{"takenAt": "2026-07-0${(it % 9) + 1}T00:00:00Z", "contentType": "image/jpeg"}"""
                            }
                        }
                    mockMvc
                        .perform(
                            authenticatedPost("/analysis", board.userId.value)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${board.id}",
                                        "photos": [$photosJson]
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("정상 contentType(image/heic)로 요청하면") {
                Then("성공 응답에 analysisId와 사진별 signed URL이 담긴다") {
                    val photosJson =
                        (0 until 90).joinToString(",") {
                            """{"takenAt": "2026-07-0${(it % 9) + 1}T00:00:00Z", "contentType": "image/heic"}"""
                        }
                    mockMvc
                        .perform(
                            authenticatedPost("/analysis", board.userId.value)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                        "boardId": "${board.id}",
                                        "photos": [$photosJson]
                                    }
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.analysisId").exists())
                        .andExpect(jsonPath("$.data.uploads.length()").value(90))
                }
            }
        }
    })
