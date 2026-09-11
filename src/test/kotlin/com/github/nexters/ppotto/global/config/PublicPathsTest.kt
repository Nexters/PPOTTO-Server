package com.github.nexters.ppotto.global.config

import com.github.nexters.ppotto.global.web.PublicPaths
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.util.AntPathMatcher

private val PATHS =
    listOf(
        "/auth/login",
        "/auth/login/web",
        "/auth/refresh",
        "/auth/logout",
        "/actuator/health",
        "/actuator/health/readiness",
        "/actuator/health/liveness",
        "/actuator/metrics",
        "/swagger-ui.html",
        "/swagger-ui/index.html",
        "/v3/api-docs",
        "/v3/api-docs/v1",
        "/terms",
        "/terms/agreements",
        "/stickers/0198c0f0-0000-7000-8000-000000000000",
        "/stickers/0198c0f0-0000-7000-8000-000000000000/view",
        "/stickers/shared/01983f30-0000-7000-8000-000000000000",
        "/boards",
        "/users/me",
    )

private val matcher = AntPathMatcher()

private fun Array<String>.matches(path: String): Boolean = any { matcher.match(it, path) }

class PublicPathsTest :
    BehaviorSpec({
        Given("보안 체인이 쓰는 ant 패턴과 Bearer 필터가 쓰는 판정 함수가 있을 때") {
            When("모든 대표 경로를 PUBLIC_API_PATTERNS와 isPublicApi로 각각 판정하면") {
                val disagreements =
                    PATHS.filter { PublicPaths.PUBLIC_API_PATTERNS.matches(it) != PublicPaths.isPublicApi(it) }

                Then("두 판정이 어긋나는 경로가 없다") {
                    disagreements.shouldBeEmpty()
                }
            }

            When("모든 대표 경로를 DOCUMENT_PATTERNS와 isDocument로 각각 판정하면") {
                val disagreements =
                    PATHS.filter { PublicPaths.DOCUMENT_PATTERNS.matches(it) != PublicPaths.isDocument(it) }

                Then("두 판정이 어긋나는 경로가 없다") {
                    disagreements.shouldBeEmpty()
                }
            }
        }

        Given("체인이 permitAll 하는 공개 API 경로가 주어졌을 때") {
            When("판정하면") {
                Then("로그인·토큰 재발급·헬스체크만 필터를 건너뛴다") {
                    PublicPaths.isPublicApi("/auth/login") shouldBe true
                    PublicPaths.isPublicApi("/auth/login/web") shouldBe true
                    PublicPaths.isPublicApi("/auth/refresh") shouldBe true
                    PublicPaths.isPublicApi("/actuator/health/readiness") shouldBe true
                    PublicPaths.isPublicApi("/auth/logout") shouldBe false
                    PublicPaths.isPublicApi("/boards") shouldBe false
                }
            }
        }

        Given("익명 접근이 허용되는 optional-auth GET 경로가 주어졌을 때") {
            When("체인 패턴과 필터 판정을 함께 보면") {
                Then("체인은 익명 GET을 허용한다") {
                    PublicPaths.OPTIONAL_AUTH_GET_PATTERNS.matches("/terms") shouldBe true
                    PublicPaths.OPTIONAL_AUTH_GET_PATTERNS
                        .matches("/stickers/shared/01983f30-0000-7000-8000-000000000000") shouldBe true
                }

                Then("토큰을 해석해야 하므로 Bearer 필터는 건너뛰지 않는다") {
                    PublicPaths.isPublicApi("/terms") shouldBe false
                    PublicPaths.isPublicApi("/stickers/shared/01983f30-0000-7000-8000-000000000000") shouldBe false
                }

                Then("리캡 상세와 약관 동의, 스티커 조회 기록은 익명 허용 대상이 아니다") {
                    PublicPaths.OPTIONAL_AUTH_GET_PATTERNS.matches("/terms/agreements") shouldBe false
                    PublicPaths.OPTIONAL_AUTH_GET_PATTERNS
                        .matches("/stickers/0198c0f0-0000-7000-8000-000000000000") shouldBe false
                    PublicPaths.OPTIONAL_AUTH_GET_PATTERNS
                        .matches("/stickers/0198c0f0-0000-7000-8000-000000000000/view") shouldBe false
                }
            }
        }

        Given("문서와 actuator 경로가 주어졌을 때") {
            When("판정하면") {
                Then("swagger와 api-docs는 문서로 본다") {
                    PublicPaths.isDocument("/swagger-ui/index.html") shouldBe true
                    PublicPaths.isDocument("/v3/api-docs/v1") shouldBe true
                    PublicPaths.isDocument("/boards") shouldBe false
                }

                Then("actuator 하위 경로 전부를 actuator로 본다") {
                    PublicPaths.isActuator("/actuator/health/readiness") shouldBe true
                    PublicPaths.isActuator("/actuator/metrics") shouldBe true
                    PublicPaths.isActuator("/boards") shouldBe false
                }
            }
        }
    })
