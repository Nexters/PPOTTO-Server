package com.github.nexters.ppotto.global.config

import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.security.CurrentUser
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("뽀또 API")
                    .version("v1")
                    .description(
                        """
                        뽀또 백엔드 API 문서입니다.

                        ### 응답 형식

                        모든 응답은 공통 envelope로 내려갑니다.

                        - 성공: `{"success": true, "data": { ... }}`
                        - 실패: `{"success": false, "error": {"code": "COMMON-001", "message": "잘못된 입력입니다.", "fieldErrors": []}}`

                        ### 공통 에러 코드

                        | 코드 | 상태 | 설명 |
                        |---|---|---|
                        | COMMON-000 | 500 | 서버 오류 |
                        | COMMON-001 | 400 | 잘못된 입력 |
                        | COMMON-002 | 404 | 리소스 없음 |
                        | COMMON-003 | 405 | 허용되지 않은 메서드 |
                        | COMMON-004 | 401 | 인증 필요 |
                        | COMMON-005 | 403 | 권한 없음 |
                        | COMMON-006 | 409 | 충돌 |
                        """.trimIndent(),
                    ).contact(
                        Contact()
                            .name("Github Repository")
                            .url("https://github.com/nexters/ppotto-server"),
                    ),
            ).components(
                Components().addSecuritySchemes(
                    BEARER_AUTH_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("로그인 또는 토큰 재발급으로 받은 access token"),
                ),
            )

    @Bean
    fun operationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            operation
                .also {
                    it.addParametersItem(
                        Parameter()
                            .name(API_VERSION_HEADER)
                            .`in`("header")
                            .required(false)
                            .description("API 버전. 생략하면 1")
                            .schema(StringSchema()._default("1"))
                            .example("1"),
                    )
                }.also { customizedOperation ->
                    handlerMethod.methodParameters.let { parameters ->
                        when {
                            parameters.any { it.hasParameterAnnotation(AuthenticatedUser::class.java) } -> {
                                customizedOperation.addSecurityItem(SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                                customizedOperation.responses.addApiResponse(
                                    "401",
                                    ApiResponse().description("access token이 없거나 유효하지 않음"),
                                )
                            }

                            parameters.any { it.hasParameterAnnotation(CurrentUser::class.java) } -> {
                                customizedOperation.security =
                                    listOf(
                                        SecurityRequirement(),
                                        SecurityRequirement().addList(BEARER_AUTH_SCHEME),
                                    )
                                customizedOperation.responses.addApiResponse(
                                    "401",
                                    ApiResponse().description("전달한 access token이 유효하지 않음"),
                                )
                            }
                        }
                    }
                }
        }

    private companion object {
        const val BEARER_AUTH_SCHEME = "bearerAuth"
        const val API_VERSION_HEADER = "X-API-Version"
    }
}
