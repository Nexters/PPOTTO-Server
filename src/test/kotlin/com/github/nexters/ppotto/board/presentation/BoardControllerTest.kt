package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.hasItem
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val TOO_LONG_NAME_LENGTH = Board.MAX_NAME_LENGTH + 1

@AutoConfigureMockMvc
@Import(BoardTestConfig::class)
class BoardControllerTest(
    mockMvc: MockMvc,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("보드를 하나 만든 인증된 사용자가") {
            val user = userRepository.saveTestUser()
            val principal = UsernamePasswordAuthenticationToken.authenticated(user.id.value, null, emptyList())
            mockMvc
                .perform(
                    post("/boards")
                        .with(authentication(principal))
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.name").value(Board.defaultName(1)))
            val created = boardRepository.findByUserId(user.id).single()

            When("보드 목록을 조회하면") {
                val result =
                    mockMvc.perform(
                        get("/boards")
                            .with(authentication(principal))
                            .header("X-API-Version", "1"),
                    )

                Then("방금 만든 보드만 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[*].id").value(hasItem(created.id.toString())))
                }
            }

            When("보드 이름을 변경하면") {
                val result =
                    mockMvc.perform(
                        patch("/boards/${created.id}")
                            .with(authentication(principal))
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"여름 휴가"}"""),
                    )

                Then("변경한 이름을 응답한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.name").value("여름 휴가"))
                }

                Then("v1 상세 응답도 변경한 이름을 반환한다") {
                    mockMvc
                        .perform(
                            get("/boards/${created.id}")
                                .with(authentication(principal))
                                .header("X-API-Version", "1"),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.name").value("여름 휴가"))
                }

                Then("v2 상세 응답도 변경한 이름을 반환한다") {
                    mockMvc
                        .perform(
                            get("/boards/${created.id}")
                                .with(authentication(principal))
                                .header("X-API-Version", "2"),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.name").value("여름 휴가"))
                }
            }

            When("공백뿐인 이름으로 보드를 만들면") {
                val result =
                    mockMvc.perform(
                        post("/boards")
                            .with(authentication(principal))
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"   "}"""),
                    )

                Then("COMMON-001로 400을 응답한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }

                Then("보드를 만들지 않는다") {
                    boardRepository.findByUserId(user.id) shouldHaveSize 1
                }
            }

            When("최대 길이를 넘는 이름으로 보드를 만들면") {
                val result =
                    mockMvc.perform(
                        post("/boards")
                            .with(authentication(principal))
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"${"가".repeat(TOO_LONG_NAME_LENGTH)}"}"""),
                    )

                Then("COMMON-001로 400을 응답한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }

                Then("보드를 만들지 않는다") {
                    boardRepository.findByUserId(user.id) shouldHaveSize 1
                }
            }

            When("공백뿐인 이름으로 변경하면") {
                val result =
                    mockMvc.perform(
                        patch("/boards/${created.id}")
                            .with(authentication(principal))
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"   "}"""),
                    )

                Then("COMMON-001로 400을 응답한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }

                Then("이름을 바꾸지 않는다") {
                    boardRepository.findById(created.id)?.name shouldBe created.name
                }
            }

            When("최대 길이를 넘는 이름으로 변경하면") {
                val result =
                    mockMvc.perform(
                        patch("/boards/${created.id}")
                            .with(authentication(principal))
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"${"가".repeat(TOO_LONG_NAME_LENGTH)}"}"""),
                    )

                Then("COMMON-001로 400을 응답한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }

                Then("이름을 바꾸지 않는다") {
                    boardRepository.findById(created.id)?.name shouldBe created.name
                }
            }

            When("마지막 남은 보드를 삭제하면") {
                val result =
                    mockMvc.perform(
                        delete("/boards/${created.id}")
                            .with(authentication(principal))
                            .header("X-API-Version", "1"),
                    )

                Then("BOARD-004로 409를 응답한다") {
                    result
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.error.code").value("BOARD-004"))
                }

                Then("보드를 지우지 않는다") {
                    boardRepository.findByUserId(user.id) shouldHaveSize 1
                }
            }
        }

        Given("보드가 두 개인 인증된 사용자가") {
            val user = userRepository.saveTestUser()
            val principal = UsernamePasswordAuthenticationToken.authenticated(user.id.value, null, emptyList())
            val board = boardRepository.save(user.id)
            boardRepository.save(user.id)

            When("보드 하나를 삭제하면") {
                val result =
                    mockMvc.perform(
                        delete("/boards/${board.id}")
                            .with(authentication(principal))
                            .header("X-API-Version", "1"),
                    )

                Then("빈 성공 응답을 반환하고 보드를 지운다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data").doesNotExist())
                    boardRepository.findByUserId(user.id) shouldHaveSize 1
                }
            }
        }

        Given("다른 사용자의 보드가 있는 경우") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            val other = userRepository.saveTestUser()
            val otherPrincipal = UsernamePasswordAuthenticationToken.authenticated(other.id.value, null, emptyList())

            When("인증된 다른 사용자가 조회하면") {
                val result =
                    mockMvc.perform(
                        get("/boards/${board.id}")
                            .with(authentication(otherPrincipal))
                            .header("X-API-Version", "1"),
                    )

                Then("BOARD-002로 404를 응답하고 보드를 노출하지 않는다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                        .andExpect(jsonPath("$.data").doesNotExist())
                }
            }

            When("인증된 다른 사용자가 이름을 바꾸면") {
                val result =
                    mockMvc.perform(
                        patch("/boards/${board.id}")
                            .with(authentication(otherPrincipal))
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"뺏은 보드"}"""),
                    )

                Then("BOARD-002로 404를 응답하고 이름을 바꾸지 않는다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                    boardRepository.findById(board.id)?.name shouldBe board.name
                }
            }
        }

        Given("인증되지 않은 요청일 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)

            When("보드 목록을 조회하면") {
                val result = mockMvc.perform(get("/boards").header("X-API-Version", "1"))

                Then("COMMON-004로 401을 응답한다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }

            When("보드를 만들면") {
                val result =
                    mockMvc.perform(
                        post("/boards")
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"),
                    )

                Then("COMMON-004로 401을 응답하고 보드를 만들지 않는다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                    boardRepository.findByUserId(user.id) shouldHaveSize 1
                }
            }

            When("보드를 삭제하면") {
                val result = mockMvc.perform(delete("/boards/${board.id}").header("X-API-Version", "1"))

                Then("COMMON-004로 401을 응답하고 보드를 지우지 않는다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                    boardRepository.findByUserId(user.id) shouldHaveSize 1
                }
            }
        }
    })
