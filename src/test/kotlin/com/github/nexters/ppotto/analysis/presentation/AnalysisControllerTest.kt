package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.application.AnalysisService
import com.github.nexters.ppotto.analysis.application.model.CreateAnalysisCommand
import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.analysis.support.DEFAULT_PHOTO_GROUP_COUNT
import com.github.nexters.ppotto.analysis.support.FakePhotoStorage
import com.github.nexters.ppotto.analysis.support.burstPhotoUploadGroupsJson
import com.github.nexters.ppotto.analysis.support.photoTakenAt
import com.github.nexters.ppotto.analysis.support.photoUploadGroups
import com.github.nexters.ppotto.analysis.support.photoUploadGroupsJson
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
@Suppress("LargeClass")
class AnalysisControllerTest(
    @Autowired val mockMvc: MockMvc,
    private val photoStorage: FakePhotoStorage,
    private val photoRepository: PhotoRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    analysisRepository: AnalysisRepository,
    analysisService: AnalysisService,
    dslContext: DSLContext,
) : IntegrationTest({
        fun createAnalysisBody(
            boardId: UUID,
            photosJson: String,
        ): String = """{"boardId": "$boardId", "photos": $photosJson}"""

        Given("Board가 등록된 사용자가 분석 생성을 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)

            When("사진 목록을 담아 분석 생성을 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photoUploadGroupsJson())),
                    )

                Then("200 응답에 사진 수만큼의 업로드 URL이 담긴다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.analysisId").exists())
                        .andExpect(jsonPath("$.data.uploads.length()").value(DEFAULT_PHOTO_GROUP_COUNT))
                        .andExpect(jsonPath("$.data.uploads[0].photoId").exists())
                        .andExpect(jsonPath("$.data.uploads[0].uploadUrl").exists())
                }
            }

            When("빈 사진 배열로 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, "[]")),
                    )

                Then("400 응답을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("사진 그룹이 19개로(하한 미만) 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photoUploadGroupsJson(19))),
                    )

                Then("400 응답과 ANALYSIS-001을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-001"))
                }
            }

            When("사진 그룹이 101개로(상한 초과) 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photoUploadGroupsJson(101))),
                    )

                Then("400 응답과 ANALYSIS-001을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-001"))
                }
            }

            When("연사 그룹 내 대표 사진이 없으면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, burstPhotoUploadGroupsJson(88, listOf(false, false)))),
                    )

                Then("400 응답과 ANALYSIS-009를 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-009"))
                }
            }

            When("연사 그룹 내 대표 사진이 2장 이상이면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, burstPhotoUploadGroupsJson(88, listOf(true, true)))),
                    )

                Then("400 응답과 ANALYSIS-009를 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-009"))
                }
            }

            When("그룹당 사진이 11장으로(그룹당 상한 초과) 요청하면") {
                val photosJson = burstPhotoUploadGroupsJson(19, listOf(true) + List(10) { false })
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photosJson)),
                    )

                Then("400 응답과 ANALYSIS-010을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-010"))
                }
            }

            When("contentType 필드를 누락하고 요청하면") {
                val photosJson =
                    (0 until DEFAULT_PHOTO_GROUP_COUNT).joinToString(",", prefix = "[", postfix = "]") {
                        """{"items": [{"takenAt": "${photoTakenAt(it)}", "isRepresentative": true}]}"""
                    }
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photosJson)),
                    )

                Then("400 응답을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("contentType이 null이고 요청하면") {
                val photosJson =
                    """[{"items": [{"takenAt": "2026-07-01T00:00:00Z", "contentType": null, "isRepresentative": true}]}]"""
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photosJson)),
                    )

                Then("400 응답을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("지원하지 않는 contentType(image/gif)으로 요청하면") {
                val photosJson = photoUploadGroupsJson { if (it == 0) "image/gif" else "image/jpeg" }
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photosJson)),
                    )

                Then("400 응답을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }

            When("지원하지 않는 contentType(image/heic)으로 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photoUploadGroupsJson { "image/heic" })),
                    )

                Then("400 응답을 반환한다") {
                    response
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.success").value(false))
                }
            }
        }

        Given("이미 활성 분석이 있는 사용자가 분석 생성을 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))

            When("새 분석 생성을 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(board.userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photoUploadGroupsJson())),
                    )

                Then("409 응답과 ANALYSIS-002을 반환한다") {
                    response
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-002"))
                }
            }
        }

        Given("모든 사진이 업로드된 UPLOADING 분석에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))
            photoStorage.markUploaded(photoRepository.findPendingByAnalysisId(created.analysisId))

            When("업로드 완료를 통보하면") {
                val response = mockMvc.perform(post("/analysis/${created.analysisId}/start").authenticatedAs(board.userId))

                Then("202 응답에 업로드 90장, 실패 0장, 빈 실패 목록이 담긴다") {
                    response
                        .andExpect(status().isAccepted)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.uploadedCount").value(DEFAULT_PHOTO_GROUP_COUNT))
                        .andExpect(jsonPath("$.data.failedCount").value(0))
                        .andExpect(jsonPath("$.data.failedPhotoIds.length()").value(0))
                }
            }
        }

        Given("사진이 한 장도 업로드되지 않은 UPLOADING 분석에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))

            When("업로드 완료를 통보하면") {
                val response = mockMvc.perform(post("/analysis/${created.analysisId}/start").authenticatedAs(board.userId))

                Then("409 응답과 ANALYSIS-008을 반환한다") {
                    response
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-008"))
                }
            }
        }

        Given("존재하지 않는 boardId로 분석 생성을 요청할 때") {
            val userId = userRepository.saveTestUser().id

            When("분석 생성을 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(UUID.randomUUID(), photoUploadGroupsJson())),
                    )

                Then("404 응답과 BOARD-002를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                }
            }
        }

        Given("다른 사용자의 Board로 분석 생성을 요청할 때") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val otherUserId = userRepository.saveTestUser().id

            When("분석 생성을 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .authenticatedAs(otherUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(ownerBoard.id.value, photoUploadGroupsJson())),
                    )

                Then("404 응답과 BOARD-002를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                }
            }
        }

        Given("존재하지 않는 analysisId로 업로드 완료를 통보할 때") {
            val userId = userRepository.saveTestUser().id

            When("업로드 완료를 통보하면") {
                val response = mockMvc.perform(post("/analysis/${UUID.randomUUID()}/start").authenticatedAs(userId))

                Then("404 응답과 ANALYSIS-005를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }
        }

        Given("다른 사용자의 analysisId로 업로드 완료를 통보할 때") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(ownerBoard.userId, ownerBoard.id, CreateAnalysisCommand(photoUploadGroups()))
            val otherUserId = userRepository.saveTestUser().id

            When("업로드 완료를 통보하면") {
                val response = mockMvc.perform(post("/analysis/${created.analysisId}/start").authenticatedAs(otherUserId))

                Then("404 응답과 ANALYSIS-005를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }
        }

        Given("활성 분석이 없는 사용자로") {
            val userId = userRepository.saveTestUser().id

            When("진행 중 분석을 조회하면") {
                val response = mockMvc.perform(get("/analysis/active").authenticatedAs(userId))

                Then("200 응답과 null data를 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }
            }
        }

        Given("UPLOADING 상태의 분석을 조회할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)

            When("진행 중 분석을 조회하면") {
                val response = mockMvc.perform(get("/analysis/active").authenticatedAs(board.userId))

                Then("진행률 0의 UPLOADING 상태를 시각 필드 없이 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.id").value(analysis.id.toString()))
                        .andExpect(jsonPath("$.data.boardId").value(board.id.toString()))
                        .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                        .andExpect(jsonPath("$.data.progress").value(0))
                        .andExpect(jsonPath("$.data.failedCode").doesNotExist())
                        .andExpect(jsonPath("$.data.failedReason").doesNotExist())
                        .andExpect(jsonPath("$.data.startedAt").doesNotExist())
                        .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                }
            }
        }

        Given("ANALYZING 상태의 분석을 조회할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .set(ANALYSIS.PROGRESS, 10)
                .set(ANALYSIS.STARTED_AT, Instant.parse("2026-07-27T05:02:11Z"))
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            When("분석 상태를 조회하면") {
                val response = mockMvc.perform(get("/analysis/${analysis.id}").authenticatedAs(board.userId))

                Then("로딩 화면 폴링용 진행률과 시작 시각을 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.id").value(analysis.id.toString()))
                        .andExpect(jsonPath("$.data.boardId").value(board.id.toString()))
                        .andExpect(jsonPath("$.data.status").value("ANALYZING"))
                        .andExpect(jsonPath("$.data.progress").value(10))
                        .andExpect(jsonPath("$.data.failedCode").doesNotExist())
                        .andExpect(jsonPath("$.data.startedAt").value("2026-07-27T05:02:11Z"))
                        .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                }
            }
        }

        Given("COMPLETED 상태의 분석을 조회할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
                .set(ANALYSIS.PROGRESS, 100)
                .set(ANALYSIS.COMPLETED_AT, Instant.parse("2026-07-27T05:03:38Z"))
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            When("진행 중 분석을 조회하면") {
                val response = mockMvc.perform(get("/analysis/active").authenticatedAs(board.userId))

                Then("active에는 포함하지 않는다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }
            }

            When("분석 상태를 조회하면") {
                val response = mockMvc.perform(get("/analysis/${analysis.id}").authenticatedAs(board.userId))

                Then("진행률 100과 완료 시각을 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.data.progress").value(100))
                        .andExpect(jsonPath("$.data.failedCode").doesNotExist())
                        .andExpect(jsonPath("$.data.completedAt").value("2026-07-27T05:03:38Z"))
                }
            }
        }

        Given("실패 코드가 저장된 분석을 조회할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)

            listOf(
                AnalysisErrorCode.INVALID_GEMINI_RESPONSE to "ANALYSIS-007",
                AnalysisErrorCode.NO_STICKER_SUBJECT to "ANALYSIS-012",
                AnalysisErrorCode.STICKER_GENERATION_FAILED to "ANALYSIS-013",
                AnalysisErrorCode.RESULT_SAVE_FAILED to "ANALYSIS-014",
                AnalysisErrorCode.INTERNAL_ERROR to "ANALYSIS-015",
                AnalysisErrorCode.CLASSIFICATION_FAILED to "ANALYSIS-017",
            ).forEach { (failedCode, code) ->
                When("실패 코드가 $code 이면") {
                    dslContext
                        .update(ANALYSIS)
                        .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
                        .set(ANALYSIS.FAILED_CODE, code)
                        .set(ANALYSIS.FAILED_REASON, "분석 처리 중 실패했습니다.")
                        .where(ANALYSIS.ID.eq(analysis.id))
                        .execute()
                    val response = mockMvc.perform(get("/analysis/${analysis.id}").authenticatedAs(board.userId))

                    Then("HTTP 오류가 아닌 성공 응답의 데이터에 코드 문자열을 담는다") {
                        analysisRepository.findById(analysis.id)!!.failedCode shouldBe failedCode
                        response
                            .andExpect(status().isOk)
                            .andExpect(jsonPath("$.success").value(true))
                            .andExpect(jsonPath("$.data.status").value("FAILED"))
                            .andExpect(jsonPath("$.data.failedCode").value(code))
                            .andExpect(jsonPath("$.data.failedReason").value("분석 처리 중 실패했습니다."))
                            .andExpect(jsonPath("$.error").doesNotExist())
                    }
                }
            }
        }

        Given("실패 코드 없이 저장된 과거 분석을 조회할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
                .set(ANALYSIS.FAILED_REASON, "과거에 기록된 실패 사유")
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            When("분석 상태를 조회하면") {
                val response = mockMvc.perform(get("/analysis/${analysis.id}").authenticatedAs(board.userId))

                Then("기존 실패 사유를 유지하고 null인 실패 코드 필드는 생략한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.status").value("FAILED"))
                        .andExpect(jsonPath("$.data.failedCode").doesNotExist())
                        .andExpect(jsonPath("$.data.failedReason").value("과거에 기록된 실패 사유"))
                        .andExpect(jsonPath("$.error").doesNotExist())
                }
            }
        }

        Given("다른 사용자의 analysisId로 분석 상태를 조회할 때") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(ownerBoard.userId, ownerBoard.id)
            val otherUserId = userRepository.saveTestUser().id

            When("분석 상태를 조회하면") {
                val response = mockMvc.perform(get("/analysis/${analysis.id}").authenticatedAs(otherUserId))

                Then("404 응답과 ANALYSIS-005를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }
        }

        Given("인증되지 않은 요청으로 진행 중 분석을 조회할 때") {
            When("진행 중 분석을 조회하면") {
                val response = mockMvc.perform(get("/analysis/active"))

                Then("401 응답과 COMMON-004를 반환한다") {
                    response
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }

        Given("인증되지 않은 요청으로 분석 생성을 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)

            When("분석 생성을 요청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createAnalysisBody(board.id.value, photoUploadGroupsJson())),
                    )

                Then("401 응답과 COMMON-004를 반환한다") {
                    response
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }

        Given("UPLOADING 상태의 분석을 취소할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))

            When("취소를 요청하면") {
                val response = mockMvc.perform(delete("/analysis/${created.analysisId}").authenticatedAs(board.userId))

                Then("200 응답과 null data를 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }

                Then("분석은 FAILED/CANCELED로 닫힌다") {
                    val analysis = analysisRepository.findById(created.analysisId)
                    analysis.shouldNotBeNull()
                    analysis.status shouldBe AnalysisStatus.FAILED
                    analysis.failedCode shouldBe AnalysisErrorCode.ANALYSIS_CANCELED
                    analysis.failedCode?.code shouldBe "ANALYSIS-016"
                    analysis.failedReason shouldBe "CANCELED"
                }

                Then("분석 상태 조회에 취소 코드를 문자열로 반환한다") {
                    mockMvc
                        .perform(get("/analysis/${created.analysisId}").authenticatedAs(board.userId))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.status").value("FAILED"))
                        .andExpect(jsonPath("$.data.failedCode").value("ANALYSIS-016"))
                        .andExpect(jsonPath("$.data.failedReason").value("CANCELED"))
                        .andExpect(jsonPath("$.error").doesNotExist())
                }
            }
        }

        Given("ANALYZING 상태로 전이된 분석을 취소할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            When("취소를 요청하면") {
                val response = mockMvc.perform(delete("/analysis/${analysis.id}").authenticatedAs(board.userId))

                Then("200 응답과 null data를 반환한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }

                Then("분석은 FAILED/CANCELED로 닫힌다") {
                    val canceled = analysisRepository.findById(analysis.id)
                    canceled.shouldNotBeNull()
                    canceled.status shouldBe AnalysisStatus.FAILED
                    canceled.failedCode shouldBe AnalysisErrorCode.ANALYSIS_CANCELED
                    canceled.failedReason shouldBe "CANCELED"
                }
            }
        }

        Given("COMPLETED 상태로 전이된 분석을 취소할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            When("취소를 요청하면") {
                val response = mockMvc.perform(delete("/analysis/${analysis.id}").authenticatedAs(board.userId))

                Then("409 응답과 ANALYSIS-004를 반환한다") {
                    response
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-004"))
                }
            }
        }

        Given("다른 사용자의 analysisId로 취소를 요청할 때") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(ownerBoard.userId, ownerBoard.id)
            val otherUser = userRepository.saveTestUser()

            When("취소를 요청하면") {
                val response = mockMvc.perform(delete("/analysis/${analysis.id}").authenticatedAs(otherUser.id))

                Then("404 응답과 ANALYSIS-005를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }
        }

        Given("인증되지 않은 요청으로 취소를 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)

            When("취소를 요청하면") {
                val response = mockMvc.perform(delete("/analysis/${analysis.id}"))

                Then("401 응답과 COMMON-004를 반환한다") {
                    response
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }

        Given("UPLOADING 상태의 분석에 업로드 URL 재발급을 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(board.userId, board.id, CreateAnalysisCommand(photoUploadGroups()))

            When("업로드 URL 재발급을 요청하면") {
                val response = mockMvc.perform(post("/analysis/${created.analysisId}/reissue").authenticatedAs(board.userId))

                Then("200 응답에 PENDING 사진 90장의 URL이 담긴다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.uploads.length()").value(DEFAULT_PHOTO_GROUP_COUNT))
                        .andExpect(jsonPath("$.data.uploads[0].photoId").exists())
                        .andExpect(jsonPath("$.data.uploads[0].uploadUrl").exists())
                }
            }
        }

        Given("존재하지 않는 analysisId로 업로드 URL 재발급을 요청할 때") {
            val userId = userRepository.saveTestUser().id

            When("업로드 URL 재발급을 요청하면") {
                val response = mockMvc.perform(post("/analysis/${UUID.randomUUID()}/reissue").authenticatedAs(userId))

                Then("404 응답과 ANALYSIS-005를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }
        }

        Given("다른 사용자의 analysisId로 업로드 URL 재발급을 요청할 때") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val created = analysisService.createAnalysis(ownerBoard.userId, ownerBoard.id, CreateAnalysisCommand(photoUploadGroups()))
            val otherUserId = userRepository.saveTestUser().id

            When("업로드 URL 재발급을 요청하면") {
                val response = mockMvc.perform(post("/analysis/${created.analysisId}/reissue").authenticatedAs(otherUserId))

                Then("404 응답과 ANALYSIS-005를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }
        }

        Given("ANALYZING 상태로 전이된 분석에 업로드 URL 재발급을 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            When("업로드 URL 재발급을 요청하면") {
                val response = mockMvc.perform(post("/analysis/${analysis.id}/reissue").authenticatedAs(board.userId))

                Then("409 응답과 ANALYSIS-003을 반환한다") {
                    response
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-003"))
                }
            }
        }

        Given("인증되지 않은 요청으로 업로드 URL 재발급을 요청할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)

            When("업로드 URL 재발급을 요청하면") {
                val response = mockMvc.perform(post("/analysis/${analysis.id}/reissue"))

                Then("401 응답과 COMMON-004를 반환한다") {
                    response
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })

private fun MockHttpServletRequestBuilder.authenticatedAs(userId: UserId): MockHttpServletRequestBuilder =
    with(authentication(UsernamePasswordAuthenticationToken.authenticated(userId.value, null, emptyList())))
