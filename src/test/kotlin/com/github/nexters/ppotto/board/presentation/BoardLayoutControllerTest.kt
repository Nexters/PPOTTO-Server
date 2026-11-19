package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.domain.Drawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val TOO_LONG_TITLE_LENGTH = Sticker.MAX_TITLE_LENGTH + 1

@AutoConfigureMockMvc
@Import(BoardTestConfig::class)
class BoardLayoutControllerTest(
    mockMvc: MockMvc,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("인증된 사용자의 빈 보드가 있을 때") {
            val user = userRepository.saveTestUser()
            val principal = UsernamePasswordAuthenticationToken.authenticated(user.id.value, null, emptyList())
            val board = boardRepository.save(user.id)
            val drawingId = uuidV7()

            fun layoutRequest(
                version: String,
                body: String,
            ) = mockMvc.perform(
                patch("/boards/${board.id}/layout")
                    .with(authentication(principal))
                    .header("X-API-Version", version)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )

            fun stickerLayoutBody() =
                """
                {"stickers":[{"id":"${uuidV7()}","expectedLayoutVersion":0,"posX":80,"posY":290.5,"scale":1,
                "rotation":0,"zIndex":6,"badgeOffsetX":0,"badgeOffsetY":0,"badgeRotation":0}]}
                """.trimIndent()

            When("v1으로 선 하나를 저장하면") {
                val result =
                    layoutRequest(
                        "1",
                        """
                        {"drawings":{"created":[{"id":"$drawingId","scope":"BOARD",
                        "stroke":{"points":[[10.5,22]],"zIndex":3},"color":"#FFD400","strokeWidth":4}]}}
                        """.trimIndent(),
                    )

                Then("200을 응답한다") {
                    result.andExpect(status().isOk)
                }

                Then("겹침 순서를 stroke JSON에서 꺼내 선으로 저장한다") {
                    val stroke =
                        drawingRepository
                            .findByBoardId(board.id)
                            .single()
                            .shouldBeInstanceOf<Drawing.Stroke>()
                    stroke.zIndex shouldBe 3
                    stroke.stroke shouldBe mapOf("points" to listOf(listOf(10.5, 22.0)))
                }
            }

            When("v1으로 #RRGGBB 형식이 아닌 색상을 보내면") {
                val result =
                    layoutRequest(
                        "1",
                        """
                        {"drawings":{"created":[{"id":"$drawingId","scope":"BOARD",
                        "stroke":{"points":[[10.5,22]]},"color":"FFD400","strokeWidth":4}]}}
                        """.trimIndent(),
                    )

                Then("v1은 색상 형식을 강제하지 않으므로 200을 응답한다") {
                    result.andExpect(status().isOk)
                }

                Then("보낸 색상을 그대로 저장한다") {
                    drawingRepository
                        .findByBoardId(board.id)
                        .single()
                        .color shouldBe "FFD400"
                }
            }

            When("v2로 #RRGGBB 형식이 아닌 색상을 보내면") {
                val result =
                    layoutRequest(
                        "2",
                        """
                        {"drawings":{"created":[{"type":"STROKE","id":"$drawingId","scope":"BOARD","color":"FFD400",
                        "zIndex":3,"stroke":{"points":[[10.5,22]]},"strokeWidth":4}]}}
                        """.trimIndent(),
                    )

                Then("COMMON-001로 400을 응답한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }

                Then("그림을 저장하지 않는다") {
                    drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                }
            }

            When("v2 스티커 배치 버전을 생략하면") {
                val body = stickerLayoutBody().replace("\"expectedLayoutVersion\":0,", "")

                Then("v2에서만 입력 오류로 거부한다") {
                    layoutRequest("2", body)
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("v1 스티커 배치 요청에 버전이 없으면") {
                val body = stickerLayoutBody().replace("\"expectedLayoutVersion\":0,", "")

                Then("기존 요청 형식을 받아 소유권 검사까지 진행한다") {
                    layoutRequest("1", body)
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("BOARD-001"))
                }
            }

            When("v1으로 최대 길이를 넘는 스티커 제목을 보내면") {
                val body =
                    stickerLayoutBody()
                        .replace("\"expectedLayoutVersion\":0,", "")
                        .replace("\"posX\"", "\"title\":\"${"가".repeat(TOO_LONG_TITLE_LENGTH)}\",\"posX\"")

                Then("기존 제목 검증을 유지한다") {
                    layoutRequest("1", body)
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }
            }

            When("v2로 32자를 넘는 문구를 보내면") {
                val result =
                    layoutRequest(
                        "2",
                        """
                        {"drawings":{"created":[{"type":"TEXT","id":"$drawingId","scope":"BOARD","color":"#FFFFFF",
                        "zIndex":7,"content":"${"가".repeat(33)}","fontSize":26,"posX":80,"posY":290.5,"maxWidth":280}]}}
                        """.trimIndent(),
                    )

                Then("COMMON-001로 400을 응답한다") {
                    result
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                }

                Then("텍스트를 저장하지 않는다") {
                    drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                }
            }

            When("v2로 텍스트 하나를 저장하면") {
                val result =
                    layoutRequest(
                        "2",
                        """
                        {"drawings":{"created":[{"type":"TEXT","id":"$drawingId","scope":"BOARD","color":"#FFFFFF",
                        "zIndex":7,"content":"여름 휴가","fontSize":26,"posX":80,"posY":290.5,"maxWidth":280}]}}
                        """.trimIndent(),
                    )

                Then("200을 응답한다") {
                    result.andExpect(status().isOk)
                }

                Then("문구와 배치를 그대로 저장한다") {
                    val text =
                        drawingRepository
                            .findByBoardId(board.id)
                            .single()
                            .shouldBeInstanceOf<Drawing.Text>()
                    text.content shouldBe "여름 휴가"
                    text.zIndex shouldBe 7
                    text.maxWidth shouldBe 280.0
                }
            }

            When("v2로 기존 텍스트의 배치를 수정하면") {
                layoutRequest(
                    "2",
                    """{"drawings":{"created":[{"type":"TEXT","id":"$drawingId","scope":"BOARD",
                    "color":"#FFFFFF","zIndex":7,"content":"여름 휴가","fontSize":26,
                    "posX":80,"posY":290.5,"maxWidth":280}]}}""",
                ).andExpect(status().isOk)

                val updated =
                    layoutRequest(
                        "2",
                        """{"drawings":{"updated":[{"type":"TEXT","id":"$drawingId","scope":"BOARD",
                        "color":"#FFFFFF","zIndex":7,"content":"여름 휴가","fontSize":26,
                        "posX":120,"posY":290.5,"maxWidth":280,"expectedLayoutVersion":0}]}}""",
                    )

                Then("새 위치와 버전을 저장한다") {
                    updated.andExpect(status().isOk)
                    val text =
                        drawingRepository
                            .findByBoardId(board.id)
                            .single()
                            .shouldBeInstanceOf<Drawing.Text>()
                    text.posX shouldBe 120.0
                    text.layoutVersion shouldBe 1
                }

                Then("같은 버전의 재요청은 충돌로 거부한다") {
                    layoutRequest(
                        "2",
                        """{"drawings":{"deleted":[{"id":"$drawingId","expectedLayoutVersion":0}]}}""",
                    ).andExpect(status().isConflict)
                        .andExpect(jsonPath("$.error.code").value("BOARD-013"))
                        .andExpect(jsonPath("$.error.details.drawingIds[0]").value(drawingId.toString()))
                }
            }

            When("v2에서 버전 없이 기존 텍스트 수정을 요청하면") {
                val result =
                    layoutRequest(
                        "2",
                        """{"drawings":{"updated":[{"type":"TEXT","id":"$drawingId","scope":"BOARD",
                        "color":"#FFFFFF","zIndex":7,"content":"여름 휴가","fontSize":26,
                        "posX":120,"posY":290.5,"maxWidth":280}]}}""",
                    )

                Then("잘못된 요청으로 거부한다") {
                    result.andExpect(status().isBadRequest)
                }
            }

            When("v2에서 삭제 ID만 보내면") {
                val result = layoutRequest("2", """{"drawings":{"deletedIds":["$drawingId"]}}""")

                Then("성공으로 처리하지 않고 거부한다") {
                    result.andExpect(status().isBadRequest)
                }
            }

            When("인증 없이 레이아웃 저장을 요청하면") {
                val result =
                    mockMvc.perform(
                        patch("/boards/${board.id}/layout")
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"),
                    )

                Then("COMMON-004로 401을 응답한다") {
                    result
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.error.code").value("COMMON-004"))
                }
            }
        }
    })
