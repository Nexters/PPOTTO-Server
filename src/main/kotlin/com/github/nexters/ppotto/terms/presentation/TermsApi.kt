package com.github.nexters.ppotto.terms.presentation

import com.github.nexters.ppotto.global.openapi.InvalidInputApiResponse
import com.github.nexters.ppotto.global.response.ApiResponse
import com.github.nexters.ppotto.terms.presentation.dto.AgreeTermsRequest
import com.github.nexters.ppotto.terms.presentation.dto.TermResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID

@RequestMapping("/terms", version = "1")
@Tag(name = "약관", description = "현재 약관 조회와 사용자 동의")
interface TermsApi {
    @GetMapping
    @Operation(
        summary = "현재 유효 약관 목록 조회",
        description = "인증 없이 조회할 수 있으며 로그인한 사용자는 약관별 동의 상태도 함께 확인함",
    )
    fun findCurrentTerms(userId: UUID?): ApiResponse<List<TermResponse>>

    @PostMapping("/agreements")
    @Operation(
        summary = "약관 동의 제출",
        description = "현재 약관에 대한 동의를 저장하며 필수 약관은 모두 포함해야 함",
    )
    @InvalidInputApiResponse
    fun agree(
        userId: UUID,
        request: AgreeTermsRequest,
    ): ApiResponse<Unit>
}
