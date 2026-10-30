package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.jooq.DSLContext
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class BoardParticipantControllerTest(
    mockMvc: MockMvc,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("방장과 참여자가 있는 보드에서") {
            val owner = userRepository.saveTestUser("김용희")
            val member = userRepository.saveTestUser("이수민")
            val stranger = userRepository.saveTestUser("박영희")
            val board = boardRepository.save(owner.id, "제주 여행")
            dslContext.addBoardMember(board.id, member.id)

            When("방장이 참여자 목록을 조회하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(owner.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        get("/boards/${board.id}/participants")
                            .with(authentication(principal))
                            .header("X-API-Version", "1"),
                    )

                Then("참여자 식별자와 이름, 역할을 순서대로 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.participantCount").value(2))
                        .andExpect(jsonPath("$.data.maxParticipantCount").value(8))
                        .andExpect(jsonPath("$.data.participants[0].userId").value(owner.id.value.toString()))
                        .andExpect(jsonPath("$.data.participants[0].name").value("김용희"))
                        .andExpect(jsonPath("$.data.participants[0].role").value("OWNER"))
                        .andExpect(jsonPath("$.data.participants[1].userId").value(member.id.value.toString()))
                        .andExpect(jsonPath("$.data.participants[1].name").value("이수민"))
                        .andExpect(jsonPath("$.data.participants[1].role").value("MEMBER"))
                }
            }

            When("참여자가 최신 API 버전으로 조회하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(member.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        get("/boards/${board.id}/participants")
                            .with(authentication(principal))
                            .header("X-API-Version", "2"),
                    )

                Then("기준 버전 이후에도 동일하게 조회된다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.participantCount").value(2))
                }
            }

            When("보드와 무관한 사용자가 조회하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(stranger.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        get("/boards/${board.id}/participants")
                            .with(authentication(principal)),
                    )

                Then("BOARD-002를 반환한다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                }
            }

            When("인증 없이 조회하면") {
                val result = mockMvc.perform(get("/boards/${board.id}/participants"))

                Then("인증 오류를 반환한다") {
                    result.andExpect(status().isUnauthorized)
                }
            }
        }
    })
