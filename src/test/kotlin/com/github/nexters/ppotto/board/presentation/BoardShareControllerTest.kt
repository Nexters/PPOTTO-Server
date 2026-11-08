package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import com.jayway.jsonpath.JsonPath
import org.jooq.DSLContext
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
class BoardShareControllerTest(
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

            fun share(
                userId: UUID,
                body: String,
                version: String = "1",
            ) = mockMvc.perform(
                post("/boards/${board.id}/share")
                    .with(authentication(UsernamePasswordAuthenticationToken.authenticated(userId, null, emptyList())))
                    .header("X-API-Version", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )

            When("방장이 범위를 정해 공유하면") {
                val result = share(owner.id.value, """{"scope":"RECAP"}""")

                Then("공유 토큰과 범위를 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.shareToken").isString)
                        .andExpect(jsonPath("$.data.scope").value("RECAP"))
                }
            }

            When("참여자가 최신 API 버전으로 공유하면") {
                val result = share(member.id.value, """{"scope":"PHOTOS"}""", version = "2")

                Then("참여자도 공유할 수 있다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.scope").value("PHOTOS"))
                }
            }

            When("보드와 무관한 사용자가 공유하면") {
                val result = share(stranger.id.value, """{"scope":"RECAP"}""")

                Then("보드 존재를 노출하지 않는다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                }
            }

            When("범위를 보내지 않으면") {
                val result = share(owner.id.value, "{}")

                Then("요청 값 오류로 거부한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("허용하지 않는 범위를 보내면") {
                val result = share(owner.id.value, """{"scope":"ALL"}""")

                Then("요청 값 오류로 거부한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }
        }

        Given("로그인하지 않은 사용자가") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "제주 여행")

            When("공유하면") {
                val result =
                    mockMvc.perform(
                        post("/boards/${board.id}/share")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"scope":"RECAP"}"""),
                    )

                Then("인증 오류로 거부한다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }

        Given("방장이 보드를 공유했을 때") {
            val owner = userRepository.saveTestUser("김용희")
            val board = boardRepository.save(owner.id, "제주 여행")
            val shareToken =
                mockMvc
                    .perform(
                        post("/boards/${board.id}/share")
                            .with(authentication(UsernamePasswordAuthenticationToken.authenticated(owner.id.value, null, emptyList())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"scope":"RECAP"}"""),
                    ).andReturn()
                    .response.contentAsString
                    .let { JsonPath.read<String>(it, "$.data.shareToken") }

            When("로그인하지 않은 사용자가 공유 토큰으로 조회하면") {
                val result = mockMvc.perform(get("/boards/shared/$shareToken"))

                Then("공유된 보드를 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.boardName").value("제주 여행"))
                        .andExpect(jsonPath("$.data.sharerName").value("김용희"))
                        .andExpect(jsonPath("$.data.scope").value("RECAP"))
                        .andExpect(jsonPath("$.data.stickers").isArray)
                        .andExpect(jsonPath("$.data.drawings").isArray)
                }
            }

            When("최신 API 버전으로 조회하면") {
                val result = mockMvc.perform(get("/boards/shared/$shareToken").header("X-API-Version", "2"))

                Then("같은 응답을 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.boardName").value("제주 여행"))
                }
            }

            When("발급된 적 없는 토큰으로 조회하면") {
                val result = mockMvc.perform(get("/boards/shared/unknown-token"))

                Then("공유된 보드를 찾을 수 없다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-010"))
                }
            }
        }
    })
