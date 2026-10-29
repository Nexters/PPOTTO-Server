package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.board.support.validWebp
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.matchesPattern
import org.jooq.DSLContext
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class BoardInvitationControllerTest(
    mockMvc: MockMvc,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("방장과 참여자가 있는 보드에서") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val stranger = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "제주 여행")
            dslContext.addBoardMember(board.id, member.id)

            When("방장이 초대 코드를 발급하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(owner.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        post("/boards/${board.id}/invitations")
                            .with(authentication(principal))
                            .header("X-API-Version", "1"),
                    )

                Then("초대 발급 결과를 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.invitationId").isString)
                        .andExpect(jsonPath("$.data.code").value(matchesPattern("[A-HJ-NP-Z2-9]{6}")))
                        .andExpect(jsonPath("$.data.expiresAt").isString)
                        .andExpect(jsonPath("$.data.previewRequired").value(true))
                }
            }

            When("참여자가 초대 코드를 발급하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(member.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        post("/boards/${board.id}/invitations")
                            .with(authentication(principal))
                            .header("X-API-Version", "2"),
                    )

                Then("최신 API 버전에서도 발급된다") {
                    result.andExpect(status().isOk)
                }
            }

            When("보드와 무관한 사용자가 발급하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(stranger.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        post("/boards/${board.id}/invitations")
                            .with(authentication(principal)),
                    )

                Then("보드 존재를 노출하지 않는다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                }
            }

            When("방장이 WebP 미리보기를 업로드하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(owner.id.value, null, emptyList())
                val issued =
                    mockMvc
                        .perform(
                            post("/boards/${board.id}/invitations")
                                .with(authentication(principal)),
                        ).andReturn()
                val invitationId = JsonPath.read<String>(issued.response.contentAsString, "$.data.invitationId")
                val code = JsonPath.read<String>(issued.response.contentAsString, "$.data.code")
                val file = MockMultipartFile("file", "preview.webp", "image/webp", validWebp())
                val codePart = MockMultipartFile("code", "", "text/plain", code.toByteArray())
                val result =
                    mockMvc.perform(
                        multipart("/board-invitations/$invitationId/preview")
                            .file(file)
                            .file(codePart)
                            .with { request ->
                                request.method = "PUT"
                                request
                            }.with(authentication(principal)),
                    )

                Then("미리보기 읽기 URL을 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.previewImageUrl").value(matchesPattern("https://preview.test/.+\\.webp")))
                }
            }

            When("WebP가 아닌 파일을 업로드하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(owner.id.value, null, emptyList())
                val issued =
                    mockMvc
                        .perform(
                            post("/boards/${board.id}/invitations")
                                .with(authentication(principal)),
                        ).andReturn()
                val invitationId = JsonPath.read<String>(issued.response.contentAsString, "$.data.invitationId")
                val code = JsonPath.read<String>(issued.response.contentAsString, "$.data.code")
                val file = MockMultipartFile("file", "preview.webp", "image/webp", byteArrayOf(1, 2, 3))
                val codePart = MockMultipartFile("code", "", "text/plain", code.toByteArray())
                val result =
                    mockMvc.perform(
                        multipart("/board-invitations/$invitationId/preview")
                            .file(file)
                            .file(codePart)
                            .with { request ->
                                request.method = "PUT"
                                request
                            }.with(authentication(principal)),
                    )

                Then("BOARD-007로 거부한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("BOARD-007"))
                }
            }
        }
    })
