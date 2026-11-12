package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class BoardEventControllerTest(
    mockMvc: MockMvc,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("방장과 참여자가 있는 공동 보드에서") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val stranger = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            dslContext.addBoardMember(board.id, member.id)

            When("참여자가 보드 이벤트를 구독하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(member.id.value, null, emptyList())
                val result =
                    mockMvc
                        .perform(
                            get("/boards/${board.id}/events")
                                .with(authentication(principal))
                                .accept(MediaType.TEXT_EVENT_STREAM),
                        ).andExpect(status().isOk)
                        .andExpect(request().asyncStarted())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                        .andReturn()

                Then("heartbeat로 SSE 스트림을 연다") {
                    result.response.contentAsString shouldBe ":heartbeat\n\n"
                    result.request.asyncContext.complete()
                }
            }

            When("보드와 무관한 사용자가 구독하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(stranger.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        get("/boards/${board.id}/events")
                            .with(authentication(principal))
                            .accept(MediaType.TEXT_EVENT_STREAM),
                    )

                Then("BOARD-002를 반환한다") {
                    result.andExpect(status().isNotFound)
                }
            }

            When("인증 없이 구독하면") {
                val result =
                    mockMvc.perform(
                        get("/boards/${board.id}/events")
                            .accept(MediaType.TEXT_EVENT_STREAM),
                    )

                Then("인증 오류를 반환한다") {
                    result.andExpect(status().isUnauthorized)
                }
            }
        }
    })
