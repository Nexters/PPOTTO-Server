package com.github.nexters.ppotto.board.presentation

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.not
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import tools.jackson.databind.ObjectMapper

@AutoConfigureMockMvc
class BoardListOpenApiTest(
    mockMvc: MockMvc,
    objectMapper: ObjectMapper,
) : IntegrationTest({
        Given("버전별 OpenAPI 그룹 문서가 있을 때") {
            When("v1과 v2 문서를 조회하면") {
                val v1 = mockMvc.perform(get("/v3/api-docs/v1"))
                val v2 = mockMvc.perform(get("/v3/api-docs/v2"))

                Then("보드 목록은 v1이 id·name 배열, v2가 홈 카드 계약을 노출한다") {
                    v1
                        .andExpect(jsonPath("$['paths']['/boards']['get']['operationId']").value("list"))
                        .andExpect(jsonPath("$['components']['schemas']['BoardListV2Response']").doesNotExist())
                    v2
                        .andExpect(jsonPath("$['paths']['/boards']['get']['operationId']").value("listV2"))
                        .andExpect(
                            jsonPath("$['components']['schemas']['BoardListV2Response']['required']")
                                .value(hasItems("counts", "boards")),
                        ).andExpect(
                            jsonPath("$['components']['schemas']['BoardCountsResponse']['required']")
                                .value(hasItems("all", "personal", "shared")),
                        ).andExpect(
                            jsonPath("$['components']['schemas']['BoardSummaryV2Response']['required']")
                                .value(
                                    hasItems(
                                        "id",
                                        "name",
                                        "kind",
                                        "role",
                                        "stickerCount",
                                        "participantCount",
                                        "previewImageUrls",
                                        "lastActivityAt",
                                    ),
                                ),
                        ).andExpect(
                            jsonPath("$['components']['schemas']['BoardSummaryV2Response']['properties']['kind']['\$ref']")
                                .value("#/components/schemas/BoardKind"),
                        ).andExpect(
                            jsonPath("$['components']['schemas']['BoardSummaryV2Response']['properties']['role']['\$ref']")
                                .value("#/components/schemas/BoardRole"),
                        ).andExpect(
                            jsonPath("$['components']['schemas']['BoardKind']['enum']").value(hasItems("PERSONAL", "SHARED")),
                        ).andExpect(
                            jsonPath("$['components']['schemas']['BoardRole']['enum']").value(hasItems("OWNER", "MEMBER")),
                        ).andExpect(
                            jsonPath("$['components']['schemas']['BoardSummaryV2Response']['properties']['analysisStatus']['enum']")
                                .value(hasItems("UPLOADING", "ANALYZING")),
                        ).andExpect(
                            jsonPath("$['components']['schemas']['BoardSummaryV2Response']['required']")
                                .value(not(hasItem("mainColor"))),
                        ).andExpect(
                            jsonPath("$['components']['schemas']['BoardSummaryV2Response']['required']")
                                .value(not(hasItem("analysisStatus"))),
                        )

                    val properties =
                        objectMapper
                            .readTree(
                                v2
                                    .andReturn()
                                    .response.contentAsString,
                            ).path("components")
                            .path("schemas")
                            .path("BoardSummaryV2Response")
                            .path("properties")
                    listOf("mainColor", "analysisStatus").forEach { name ->
                        val schema = properties.path(name)
                        val type = schema.path("type")
                        (schema.path("nullable").asBoolean(false) || (type.isArray && type.any { it.asString() == "null" })) shouldBe true
                    }
                }
            }
        }
    })
