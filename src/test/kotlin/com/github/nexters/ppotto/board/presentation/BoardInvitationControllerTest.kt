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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
            val owner = userRepository.saveTestUser("김용희")
            val member = userRepository.saveTestUser("이수민")
            val stranger = userRepository.saveTestUser("박영희")
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

            When("익명 사용자가 초대 코드를 조회하면") {
                val principal = UsernamePasswordAuthenticationToken.authenticated(owner.id.value, null, emptyList())
                val issued =
                    mockMvc
                        .perform(
                            post("/boards/${board.id}/invitations")
                                .with(authentication(principal)),
                        ).andReturn()
                val code = JsonPath.read<String>(issued.response.contentAsString, "$.data.code")
                val result = mockMvc.perform(get("/board-invitations/$code"))

                Then("공개 정보만 반환한다") {
                    val expectedBoardId =
                        board.id.value
                            .toString()

                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.code").value(code))
                        .andExpect(jsonPath("$.data.boardId").value(expectedBoardId))
                        .andExpect(jsonPath("$.data.boardName").value("제주 여행"))
                        .andExpect(jsonPath("$.data.inviterName").value("김용희"))
                        .andExpect(jsonPath("$.data.participantCount").value(2))
                        .andExpect(jsonPath("$.data.maxParticipantCount").value(8))
                        .andExpect(jsonPath("$.data.stickerCount").value(0))
                        .andExpect(jsonPath("$.data.viewerStatus").doesNotExist())
                        .andExpect(jsonPath("$.data.participants").doesNotExist())
                }
            }

            When("로그인한 사용자가 초대 코드를 조회하면") {
                val ownerPrincipal =
                    UsernamePasswordAuthenticationToken.authenticated(owner.id.value, null, emptyList())
                val issued =
                    mockMvc
                        .perform(
                            post("/boards/${board.id}/invitations")
                                .with(authentication(ownerPrincipal)),
                        ).andReturn()
                val code = JsonPath.read<String>(issued.response.contentAsString, "$.data.code")
                val strangerPrincipal =
                    UsernamePasswordAuthenticationToken.authenticated(stranger.id.value, null, emptyList())
                val result =
                    mockMvc.perform(
                        get("/board-invitations/${code.lowercase()}")
                            .with(authentication(strangerPrincipal)),
                    )

                Then("참여 상태와 방장 우선 참여자 목록을 함께 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.code").value(code))
                        .andExpect(jsonPath("$.data.viewerStatus").value("NOT_JOINED"))
                        .andExpect(jsonPath("$.data.participants[0].name").value("김용희"))
                        .andExpect(jsonPath("$.data.participants[0].role").value("OWNER"))
                        .andExpect(jsonPath("$.data.participants[1].name").value("이수민"))
                        .andExpect(jsonPath("$.data.participants[1].role").value("MEMBER"))
                }
            }

            When("없는 초대 코드를 조회하면") {
                val result = mockMvc.perform(get("/board-invitations/ABCDEF"))

                Then("BOARD-006을 반환한다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-006"))
                }
            }
        }
    })
