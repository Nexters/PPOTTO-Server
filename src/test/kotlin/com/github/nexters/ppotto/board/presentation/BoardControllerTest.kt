package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.board.domain.Board
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.hamcrest.Matchers.hasItem
import org.jooq.DSLContext
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
import tools.jackson.databind.ObjectMapper

private const val TOO_LONG_NAME_LENGTH = Board.MAX_NAME_LENGTH + 1

@AutoConfigureMockMvc
@Import(BoardTestConfig::class)
class BoardControllerTest(
    mockMvc: MockMvc,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
    objectMapper: ObjectMapper,
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

        Given("다른 사용자의 보드에 참여자로 등록된 사용자가") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "공동 보드")
            val member = userRepository.saveTestUser()
            dslContext.addBoardMember(board.id, member.id)
            val memberPrincipal = UsernamePasswordAuthenticationToken.authenticated(member.id.value, null, emptyList())

            listOf("1", "2").forEach { version ->
                When("v$version 로 그 보드 상세를 조회하면") {
                    val result =
                        mockMvc.perform(
                            get("/boards/${board.id}")
                                .with(authentication(memberPrincipal))
                                .header("X-API-Version", version),
                        )

                    Then("200으로 보드를 반환한다") {
                        result
                            .andExpect(status().isOk)
                            .andExpect(jsonPath("$.data.id").value(board.id.toString()))
                            .andExpect(jsonPath("$.data.name").value("공동 보드"))
                    }
                }
            }

            When("그 보드의 이름을 바꾸면") {
                val result =
                    mockMvc.perform(
                        patch("/boards/${board.id}")
                            .with(authentication(memberPrincipal))
                            .header("X-API-Version", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"참여자 변경"}"""),
                    )

                Then("쓰기는 방장 전용이라 BOARD-002로 404를 응답하고 이름을 바꾸지 않는다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                    boardRepository.findById(board.id)?.name shouldBe "공동 보드"
                }
            }

            When("그 보드를 삭제하면") {
                val result =
                    mockMvc.perform(
                        delete("/boards/${board.id}")
                            .with(authentication(memberPrincipal))
                            .header("X-API-Version", "1"),
                    )

                Then("쓰기는 방장 전용이라 BOARD-002로 404를 응답하고 보드를 지우지 않는다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                    boardRepository.findById(board.id).shouldNotBeNull()
                }
            }

            When("v2로 그 보드의 레이아웃을 저장하면") {
                val result =
                    mockMvc.perform(
                        patch("/boards/${board.id}/layout")
                            .with(authentication(memberPrincipal))
                            .header("X-API-Version", "2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {"drawings":{"created":[{"type":"STROKE","id":"${uuidV7()}","scope":"BOARD","color":"#FFD400",
                                "zIndex":3,"stroke":{"points":[[10.5,22]]},"strokeWidth":4}]}}
                                """.trimIndent(),
                            ),
                    )

                Then("쓰기는 방장 전용이라 BOARD-002로 404를 응답하고 그림을 저장하지 않는다") {
                    result
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.error.code").value("BOARD-002"))
                    drawingRepository.findByBoardId(board.id).shouldBeEmpty()
                }
            }
        }

        Given("개인 보드 하나를 만들고 다른 사용자의 보드에 참여한 사용자가") {
            val me = userRepository.saveTestUser()
            val owner = userRepository.saveTestUser()
            val mine = boardRepository.save(me.id, "개인")
            val joined = boardRepository.save(owner.id, "참여 보드")
            dslContext.addBoardMember(joined.id, me.id)
            boardRepository.save(owner.id, "남의 보드")
            val principal = UsernamePasswordAuthenticationToken.authenticated(me.id.value, null, emptyList())

            When("v2로 보드 목록을 조회하면") {
                val body =
                    mockMvc
                        .perform(
                            get("/boards")
                                .with(authentication(principal))
                                .header("X-API-Version", "2"),
                        ).andExpect(status().isOk)
                        .andReturn()
                        .response.contentAsString
                val data = objectMapper.readTree(body)["data"]

                Then("탭별 개수와 함께 내 보드와 참여한 보드만 반환한다") {
                    data["counts"]["all"].asInt() shouldBe 2
                    data["counts"]["personal"].asInt() shouldBe 1
                    data["counts"]["shared"].asInt() shouldBe 1
                    listOf(data["boards"][0]["id"].asString(), data["boards"][1]["id"].asString()) shouldBe
                        listOf(joined.id.toString(), mine.id.toString())
                    data["boards"].size() shouldBe 2
                }

                Then("카드마다 계약된 필드를 모두 내려주고 값이 없으면 null을 명시한다") {
                    val card = data["boards"][0]
                    card.propertyNames().toList() shouldBe
                        listOf(
                            "id",
                            "name",
                            "kind",
                            "role",
                            "stickerCount",
                            "participantCount",
                            "previewImageUrls",
                            "mainColor",
                            "lastActivityAt",
                            "analysisStatus",
                        )
                    card["name"].asString() shouldBe "참여 보드"
                    card["kind"].asString() shouldBe "SHARED"
                    card["role"].asString() shouldBe "MEMBER"
                    card["stickerCount"].asInt() shouldBe 0
                    card["participantCount"].asInt() shouldBe 2
                    card["previewImageUrls"].isArray shouldBe true
                    card["previewImageUrls"].size() shouldBe 0
                    card["mainColor"].isNull shouldBe true
                    card["analysisStatus"].isNull shouldBe true
                    card["lastActivityAt"].asString() shouldMatch Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")
                }

                Then("내 개인 보드는 PERSONAL이고 내 역할은 OWNER다") {
                    val card = data["boards"][1]
                    card["kind"].asString() shouldBe "PERSONAL"
                    card["role"].asString() shouldBe "OWNER"
                    card["participantCount"].asInt() shouldBe 1
                }
            }

            When("v1로 보드 목록을 조회하면") {
                val result =
                    mockMvc.perform(
                        get("/boards")
                            .with(authentication(principal))
                            .header("X-API-Version", "1"),
                    )

                Then("기존처럼 내가 만든 보드의 id와 name만 배열로 반환한다") {
                    result
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].id").value(mine.id.toString()))
                        .andExpect(jsonPath("$.data[0].name").value("개인"))
                        .andExpect(jsonPath("$.data[0].kind").doesNotExist())
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
