package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.application.BoardInvitationService
import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.infrastructure.BoardMembershipRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class BoardParticipationControllerTest(
    mockMvc: MockMvc,
    invitationService: BoardInvitationService,
    membershipRepository: BoardMembershipRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("유효한 보드 초대가") {
            val owner = userRepository.saveTestUser("김용희")
            val newcomer = userRepository.saveTestUser("이수민")
            val board = boardRepository.save(owner.id, "제주 여행")
            val invitation = invitationService.issue(board.id, owner.id)

            When("인증 사용자가 소문자 코드로 참여하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(newcomer.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        post("/board-invitations/${invitation.code.lowercase()}/join")
                            .with(authentication(principal))
                            .header("X-API-Version", "1"),
                    )

                Then("참여한 보드와 MEMBER 역할을 반환한다") {
                    val boardId =
                        board.id.value
                            .toString()

                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.boardId").value(boardId))
                        .andExpect(jsonPath("$.data.boardName").value("제주 여행"))
                        .andExpect(jsonPath("$.data.role").value("MEMBER"))
                }
            }

            When("인증 없이 참여하면") {
                val result = mockMvc.perform(post("/board-invitations/${invitation.code}/join"))

                Then("인증 오류를 반환한다") {
                    result.andExpect(status().isUnauthorized)
                }
            }
        }

        Given("정원이 가득 찬 보드의 초대가") {
            val owner = userRepository.saveTestUser()
            val newcomer = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            repeat(Board.MAX_PARTICIPANT_COUNT - 1) {
                membershipRepository.saveIfAbsent(board.id, userRepository.saveTestUser().id)
            }
            val invitation = invitationService.issue(board.id, owner.id)

            When("신규 사용자가 참여하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(newcomer.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        post("/board-invitations/${invitation.code}/join")
                            .with(authentication(principal))
                            .header("X-API-Version", "2"),
                    )

                Then("BOARD-009를 반환한다") {
                    result
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.error.code").value("BOARD-009"))
                }
            }
        }

        Given("참여자가 있는 공동 보드에서") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            membershipRepository.saveIfAbsent(board.id, member.id)

            When("참여자가 나가기를 요청하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(member.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        delete("/boards/${board.id.value}/members/me")
                            .with(authentication(principal))
                            .header("X-API-Version", "2"),
                    )

                Then("빈 성공 응답을 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                }
            }

            When("방장이 나가기를 요청하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(owner.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        delete("/boards/${board.id.value}/members/me")
                            .with(authentication(principal)),
                    )

                Then("BOARD-012를 반환한다") {
                    result
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.error.code").value("BOARD-012"))
                }
            }

            When("인증 없이 나가기를 요청하면") {
                val result = mockMvc.perform(delete("/boards/${board.id.value}/members/me"))

                Then("인증 오류를 반환한다") {
                    result.andExpect(status().isUnauthorized)
                }
            }
        }
    })
