package com.github.nexters.ppotto.global.config

import com.github.nexters.ppotto.global.error.CommonErrorCode
import com.github.nexters.ppotto.global.openapi.ApiExampleFactory
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.security.AuthenticatedUser
import com.github.nexters.ppotto.global.security.CurrentUser
import com.github.nexters.ppotto.global.web.ApiVersions
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("뽀또 API")
                    .description(description())
                    .contact(
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
    fun apiVersionHeaderCustomizer(): OpenApiCustomizer = versionHeaderCustomizer(ApiVersions.SUPPORTED_API_VERSIONS)

    @Bean
    fun v1ApiGroup(): GroupedOpenApi = versionedGroup("1")

    @Bean
    fun v2ApiGroup(): GroupedOpenApi = versionedGroup("2")

    @Bean
    fun operationCustomizer(exampleFactory: ApiExampleFactory): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            val parameters = handlerMethod.methodParameters
            when {
                parameters.any { it.hasParameterAnnotation(AuthenticatedUser::class.java) } -> {
                    operation.addSecurityItem(SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                    operation.responses.addApiResponse(
                        "401",
                        unauthorizedApiResponse(exampleFactory, "access token이 없거나 유효하지 않음 (COMMON-004)"),
                    )
                }

                parameters.any { it.hasParameterAnnotation(CurrentUser::class.java) } -> {
                    operation.security =
                        listOf(
                            SecurityRequirement(),
                            SecurityRequirement().addList(BEARER_AUTH_SCHEME),
                        )
                    operation.responses.addApiResponse(
                        "401",
                        unauthorizedApiResponse(exampleFactory, "전달한 access token이 유효하지 않음 (COMMON-004)"),
                    )
                }
            }
            operation
        }

    private fun description(): String =
        """
        뽀또 백엔드 API 문서입니다.

        ### 응답 형식

        모든 응답은 공통 envelope로 내려갑니다.

        - 성공: `{"success": true, "data": { ... }, "error": null}`
        - 실패: `{"success": false, "data": null, "error": {"code": "COMMON-001", "message": "잘못된 입력입니다.", "fieldErrors": [], "timestamp": "2026-07-27T05:02:11Z"}}`

        ### 공통 에러 코드

        | 코드 | 상태 | 설명 |
        |---|---|---|
        """.trimIndent() + "\n" + commonErrorRows()

    private fun commonErrorRows(): String =
        CommonErrorCode.entries.joinToString("\n") { "| ${it.code} | ${it.status.value()} | ${it.message} |" }

    private fun unauthorizedApiResponse(
        exampleFactory: ApiExampleFactory,
        description: String,
    ): ApiResponse =
        ApiResponse()
            .description(description)
            .content(
                Content().addMediaType(
                    APPLICATION_JSON,
                    MediaType()
                        .schema(Schema<Any>().`$ref`(API_ERROR_RESPONSE_REF))
                        .addExamples("COMMON-004", exampleFactory.createUnnamed(ApiExamples.UNAUTHORIZED)),
                ),
            )

    private fun versionedGroup(version: String): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("v$version")
            .addOpenApiMethodFilter { version in ApiVersions.acceptedVersionsOf(it.declaringClass) }
            .addOpenApiCustomizer(versionHeaderCustomizer(listOf(version)))
            .build()

    private fun versionHeaderCustomizer(versions: List<String>): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.paths
                .orEmpty()
                .values
                .flatMap { it.readOperations() }
                .flatMap { it.parameters.orEmpty() }
                .filter { it.name == ApiVersions.API_VERSION_HEADER }
                .forEach { parameter ->
                    parameter
                        .description(API_VERSION_DESCRIPTION)
                        .required(false)
                        .example(versions.first())
                        .schema(
                            StringSchema()
                                ._default(versions.first())
                                ._enum(versions),
                        )
                }
        }

    private companion object {
        const val BEARER_AUTH_SCHEME = "bearerAuth"
        const val API_VERSION_DESCRIPTION = "API 버전. 생략하면 서버 기본값 ${ApiVersions.DEFAULT_API_VERSION}로 처리합니다"
        const val APPLICATION_JSON = "application/json"
        const val API_ERROR_RESPONSE_REF = "#/components/schemas/ApiErrorResponse"
    }
}
