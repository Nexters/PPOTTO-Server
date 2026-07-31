package com.github.nexters.ppotto.terms.presentation

import com.github.nexters.ppotto.global.openapi.ApiErrorResponse
import com.github.nexters.ppotto.global.openapi.ApiExamples
import com.github.nexters.ppotto.global.openapi.EmptySuccessApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.terms.presentation.dto.AgreeTermsRequest
import com.github.nexters.ppotto.terms.presentation.dto.TermResponse
import com.github.nexters.ppotto.terms.presentation.dto.TermsApiExamples
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RequestMapping("/terms", version = "1")
@Tag(name = "약관", description = "현재 약관 조회와 사용자 동의")
interface TermsApi {
    @GetMapping
    @Operation(
        summary = "현재 유효 약관 목록 조회",
        description = "인증 없이 조회할 수 있으며 로그인한 사용자는 약관별 동의 상태도 함께 확인함",
    )
    @OpenApiResponse(
        responseCode = "200",
        useReturnTypeSchema = true,
        description = "code별 현재 유효 버전 1건씩",
        content = [
            Content(
                examples = [
                    ExampleObject(
                        name = "로그인 사용자 - 동의 상태 포함",
                        value = TermsApiExamples.CURRENT_TERMS_RESPONSE,
                    ),
                    ExampleObject(
                        name = "비로그인 - agreed는 모두 false",
                        value = TermsApiExamples.ANONYMOUS_TERMS_RESPONSE,
                    ),
                ],
            ),
        ],
    )
    fun findCurrentTerms(userId: UUID?): ApiResponse<List<TermResponse>>

    @PostMapping("/agreements")
    @Operation(
        summary = "약관 동의 제출",
        description = "현재 약관에 대한 동의를 저장하며 필수 약관은 모두 포함해야 함. 이미 동의한 약관은 무시됨 (멱등)",
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = AgreeTermsRequest::class),
                        examples = [
                            ExampleObject(
                                name = "필수 약관 동의",
                                value = TermsApiExamples.AGREE_TERMS_REQUEST,
                            ),
                        ],
                    ),
                ],
            ),
    )
    @EmptySuccessApiResponse
    @OpenApiResponse(
        responseCode = "400",
        description = "요청 값이 올바르지 않음 (COMMON-001, TERM-001)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "COMMON-001",
                        summary = "요청 바디 검증 실패",
                        value = ApiExamples.INVALID_INPUT,
                    ),
                    ExampleObject(
                        name = "TERM-001",
                        summary = "필수 약관 미포함",
                        value = TermsApiExamples.REQUIRED_TERMS_MISSING,
                    ),
                ],
            ),
        ],
    )
    fun agree(
        userId: UUID,
        request: AgreeTermsRequest,
    ): ApiResponse<Unit>
}
