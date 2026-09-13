package com.github.nexters.ppotto.analysis.presentation

import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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
class AnalysisNotificationControllerTest(
    @Autowired val mockMvc: MockMvc,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("본인의 ANALYZING 분석이 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            analysisRepository.markAnalyzing(analysis.id, Instant.now())

            When("완료 알림을 신청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis/${analysis.id}/notifications").authenticatedAs(board.userId),
                    )

                Then("200 응답을 반환하고 해당 분석에 신청 시각을 기록한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())

                    analysisRepository
                        .findById(analysis.id)
                        ?.notificationRequestedAt
                        .shouldNotBeNull()

                    mockMvc
                        .perform(get("/analysis/${analysis.id}").authenticatedAs(board.userId))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.notificationRequested").value(true))
                }
            }
        }

        Given("이미 완료 알림을 신청한 ANALYZING 분석이 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            analysisRepository.markAnalyzing(analysis.id, Instant.now())
            mockMvc.perform(post("/analysis/${analysis.id}/notifications").authenticatedAs(board.userId))
            val firstRequestedAt = analysisRepository.findById(analysis.id)?.notificationRequestedAt

            When("완료 알림을 다시 신청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis/${analysis.id}/notifications").authenticatedAs(board.userId),
                    )

                Then("동일하게 성공하고 최초 신청 시각을 유지한다") {
                    response.andExpect(status().isOk)
                    analysisRepository.findById(analysis.id)?.notificationRequestedAt shouldBe firstRequestedAt
                }
            }
        }

        Given("아직 시작하지 않은 UPLOADING 분석이 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)

            When("완료 알림을 신청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis/${analysis.id}/notifications").authenticatedAs(board.userId),
                    )

                Then("409 응답과 ANALYSIS-018을 반환한다") {
                    response
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-018"))
                }
            }
        }

        Given("다른 사용자의 ANALYZING 분석이 있을 때") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(ownerBoard.userId, ownerBoard.id)
            analysisRepository.markAnalyzing(analysis.id, Instant.now())
            val otherUserId = userRepository.saveTestUser().id

            When("완료 알림을 신청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis/${analysis.id}/notifications").authenticatedAs(otherUserId),
                    )

                Then("404 응답과 ANALYSIS-005를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }
        }

        Given("존재하지 않는 분석 ID가 있을 때") {
            val userId = userRepository.saveTestUser().id

            When("완료 알림을 신청하면") {
                val response =
                    mockMvc.perform(
                        post("/analysis/${UUID.randomUUID()}/notifications").authenticatedAs(userId),
                    )

                Then("404 응답과 ANALYSIS-005를 반환한다") {
                    response
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("ANALYSIS-005"))
                }
            }
        }

        Given("인증하지 않은 사용자가 있을 때") {
            When("완료 알림을 신청하면") {
                val response = mockMvc.perform(post("/analysis/${UUID.randomUUID()}/notifications"))

                Then("401 응답과 COMMON-004를 반환한다") {
                    response
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })

private fun MockHttpServletRequestBuilder.authenticatedAs(userId: UserId): MockHttpServletRequestBuilder =
    with(authentication(UsernamePasswordAuthenticationToken.authenticated(userId.value, null, emptyList())))
