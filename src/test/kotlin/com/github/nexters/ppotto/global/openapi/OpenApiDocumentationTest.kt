package com.github.nexters.ppotto.global.openapi

import com.github.nexters.ppotto.support.IntegrationTest
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.hasSize
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class OpenApiDocumentationTest(
    mockMvc: MockMvc,
) : IntegrationTest({
        Given("OpenAPI 문서를 조회하면") {
            val result = mockMvc.perform(get("/v3/api-docs"))

            Then("서비스 정보와 Bearer 인증 스키마를 제공한다") {
                result
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.info.title").value("뽀또 API"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            }

            Then("엔드포인트 설명을 제공한다") {
                result
                    .andExpect(jsonPath("$['paths']['/analysis']['post']['summary']").value("분석 생성"))
                    .andExpect(jsonPath("$['paths']['/analysis/active']['get']['summary']").value("진행 중 분석 조회"))
                    .andExpect(jsonPath("$['paths']['/analysis/{analysisId}']['get']['summary']").value("분석 상태 조회"))
                    .andExpect(jsonPath("$['paths']['/analysis']['post']['tags']").value(hasItem("분석")))
                    .andExpect(jsonPath("$['paths']['/auth/login']['post']['summary']").value("소셜 로그인"))
                    .andExpect(jsonPath("$['paths']['/users/me']['get']['summary']").value("내 정보 조회"))
                    .andExpect(jsonPath("$['paths']['/boards']['get']['summary']").value("내 보드 목록 조회"))
                    .andExpect(
                        jsonPath("$['paths']['/boards/{boardId}/layout']['patch']['summary']")
                            .value("보드 편집 결과 저장"),
                    ).andExpect(
                        jsonPath("$['paths']['/stickers/{stickerId}']['get']['summary']")
                            .value("리캡 상세 조회"),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['CreateAnalysisRequest']['description']")
                            .value("분석 생성과 사진 업로드 URL 발급 요청"),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['AnalysisStatusResponse']['description']")
                            .value("분석 상태"),
                    )
            }

            Then("API 버전 헤더를 operation마다 하나만 노출한다") {
                result
                    .andExpect(
                        jsonPath("$['paths']['/analysis']['post']['parameters'][?(@.name == 'X-API-Version')]")
                            .value(hasSize<Any>(1)),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/boards/{boardId}']['get']['parameters'][?(@.name == 'X-API-Version')]",
                        ).value(hasSize<Any>(1)),
                    ).andExpect(
                        jsonPath("$['paths']['/terms']['get']['parameters'][?(@.name == 'X-API-Version')]")
                            .value(hasSize<Any>(1)),
                    )
            }

            Then("API 버전 헤더 기본값과 설명을 명세와 맞춘다") {
                result
                    .andExpect(
                        jsonPath(
                            "$['paths']['/analysis']['post']['parameters'][?(@.name == 'X-API-Version')].description",
                        ).value(hasItem("API 버전. 생략하면 서버 기본값 1로 처리합니다")),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/analysis']['post']['parameters'][?(@.name == 'X-API-Version')].schema.default",
                        ).value(hasItem("1")),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/boards/{boardId}']['get']" +
                                "['parameters'][?(@.name == 'X-API-Version')].schema.enum",
                        ).value(hasItem(listOf("1"))),
                    )
            }

            Then("필수 인증과 선택 인증을 구분한다") {
                result
                    .andExpect(
                        jsonPath("$['paths']['/analysis']['post']['security'][0]['bearerAuth']").isArray,
                    ).andExpect(
                        jsonPath("$['paths']['/analysis/active']['get']['security'][0]['bearerAuth']").isArray,
                    ).andExpect(
                        jsonPath("$['paths']['/analysis/{analysisId}']['get']['security'][0]['bearerAuth']").isArray,
                    ).andExpect(
                        jsonPath("$['paths']['/terms']['get']['security'][1]['bearerAuth']").isArray,
                    ).andExpect(
                        jsonPath("$['paths']['/analysis']['post']['responses']['401']['description']")
                            .value("access token이 없거나 유효하지 않음 (COMMON-004)"),
                    ).andExpect(
                        jsonPath("$['paths']['/terms']['get']['responses']['401']['description']")
                            .value("전달한 access token이 유효하지 않음 (COMMON-004)"),
                    )
            }

            Then("성공 응답에 공통 봉투 스키마와 상황별 예시를 함께 제공한다") {
                result
                    .andExpect(
                        jsonPath(
                            "$['paths']['/auth/login']['post']['responses']['200']" +
                                "['content']['application/json']['schema']['\$ref']",
                        ).value("#/components/schemas/ApiResponseLoginResponse"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/auth/login']['post']['responses']['200']" +
                                "['content']['application/json']['examples']['신규 가입 - 약관 동의 필요']" +
                                "['value']['data']['isNewUser']",
                        ).value(true),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/auth/login']['post']['responses']['200']" +
                                "['content']['application/json']['examples']['재로그인 - 바로 보드 진입']" +
                                "['value']['data']['isNewUser']",
                        ).value(false),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/analysis/{analysisId}']['get']['responses']['200']" +
                                "['content']['application/json']['examples']['완료']['value']['data']['status']",
                        ).value("COMPLETED"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/users/me']['delete']['responses']['200']" +
                                "['content']['application/json']['examples']['성공']['value']['success']",
                        ).value(true),
                    )
            }

            Then("성공 응답 예시를 운영 ObjectMapper 설정 그대로 직렬화한다") {
                result
                    .andExpect(
                        jsonPath(
                            "$['paths']['/users/me']['get']['responses']['200']" +
                                "['content']['application/json']['examples']['카카오 사용자']['value']['data']['createdAt']",
                        ).value("2026-07-01T00:12:33Z"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/users/me']['get']['responses']['200']" +
                                "['content']['application/json']['examples']['카카오 사용자']['value']['error']",
                        ).doesNotExist(),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/boards/{boardId}']['get']['responses']['200']" +
                                "['content']['application/json']['examples']['스티커와 그림이 있는 보드']" +
                                "['value']['data']['stickers'][0]['posY']",
                        ).value(318.0),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/analysis/active']['get']['responses']['200']" +
                                "['content']['application/json']['examples']['진행 중 분석 없음']['value']['data']",
                        ).doesNotExist(),
                    )
            }

            Then("요청 바디에 provider별 예시를 제공한다") {
                result
                    .andExpect(
                        jsonPath(
                            "$['paths']['/auth/login']['post']['requestBody']" +
                                "['content']['application/json']['examples']['카카오 로그인']['value']['provider']",
                        ).value("KAKAO"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/auth/login']['post']['requestBody']" +
                                "['content']['application/json']['examples']['애플 최초 로그인']['value']['provider']",
                        ).value("APPLE"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/boards/{boardId}/layout']['patch']['requestBody']" +
                                "['content']['application/json']['examples']" +
                                "['드로잉 모드 종료 (생성 2건, 삭제 1건)']['value']['drawings']['deletedIds']",
                        ).value(hasSize<Any>(1)),
                    )
            }

            Then("실패 응답에 도메인 에러 코드와 메시지를 노출한다") {
                result
                    .andExpect(
                        jsonPath("$['paths']['/analysis']['post']['responses']['404']['description']")
                            .value("보드를 찾을 수 없음 (BOARD-002)"),
                    ).andExpect(
                        jsonPath("$['paths']['/analysis/{analysisId}']['get']['responses']['404']['description']")
                            .value("분석을 찾을 수 없음 (ANALYSIS-005)"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/auth/login']['post']['responses']['401']" +
                                "['content']['application/json']['examples']['AUTH-001']['value']['error']['code']",
                        ).value("AUTH-001"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/auth/login']['post']['responses']['401']" +
                                "['content']['application/json']['examples']['AUTH-003']['value']['error']['message']",
                        ).value("애플 인증 코드 교환에 실패했습니다. 다시 로그인해 주세요."),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/boards/{boardId}']['delete']['responses']['409']" +
                                "['content']['application/json']['examples']['BOARD-004']['value']['error']['code']",
                        ).value("BOARD-004"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/boards/{boardId}']['delete']['responses']['409']" +
                                "['content']['application/json']['examples']['BOARD-005']['value']['error']['code']",
                        ).value("BOARD-005"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/stickers/{stickerId}']['get']['responses']['404']" +
                                "['content']['application/json']['examples']['STICKER-001']['value']['error']['code']",
                        ).value("STICKER-001"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/terms/agreements']['post']['responses']['400']" +
                                "['content']['application/json']['examples']['TERM-001']['value']['error']['code']",
                        ).value("TERM-001"),
                    ).andExpect(
                        jsonPath(
                            "$['paths']['/users/me']['get']['responses']['401']" +
                                "['content']['application/json']['examples']['COMMON-004']['value']['error']['code']",
                        ).value("COMMON-004"),
                    )
            }

            Then("스키마 필드 이름과 예시가 실제 응답 필드와 일치한다") {
                result
                    .andExpect(
                        jsonPath("$['components']['schemas']['StickerResponse']['properties']['isNew']['example']")
                            .value(false),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['StickerResponse']['properties']['zIndex']['example']")
                            .value(3),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['TermResponse']['properties']['isRequired']['example']")
                            .value(true),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['LoginRequest']['properties']['provider']['example']")
                            .value("KAKAO"),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['ErrorResponse']['properties']['code']['example']")
                            .value("COMMON-001"),
                    )
            }

            Then("Kotlin non-null 필드와 요청 제약을 스키마에 반영한다") {
                result
                    .andExpect(
                        jsonPath("$['components']['schemas']['ApiResponseLoginResponse']['required']")
                            .value(hasItems("success")),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['ApiErrorResponse']['required']")
                            .value(hasItems("success", "error")),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['LoginResponse']['required']")
                            .value(
                                hasItems(
                                    "accessToken",
                                    "refreshToken",
                                    "accessTokenExpiresIn",
                                    "isNewUser",
                                    "pendingTerms",
                                ),
                            ),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['AgreeTermsRequest']['required']")
                            .value(hasItems("termIds")),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['CreateAnalysisRequest']['required']")
                            .value(hasItems("boardId", "photos")),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['CreateAnalysisRequest']['properties']['photos']['minItems']")
                            .value(90),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['CreateAnalysisRequest']['properties']['photos']['maxItems']")
                            .value(100),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['DrawingCreateRequest']['required']")
                            .value(hasItems("id", "scope", "stroke", "color", "strokeWidth")),
                    ).andExpect(
                        jsonPath("$['components']['schemas']['StickerLayoutRequest']['required']")
                            .value(hasItems("id", "posX", "posY", "scale", "rotation", "zIndex")),
                    )
            }
        }
    })
