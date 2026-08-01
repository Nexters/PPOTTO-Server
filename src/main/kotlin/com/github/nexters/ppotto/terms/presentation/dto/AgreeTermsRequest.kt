package com.github.nexters.ppotto.terms.presentation.dto

import com.github.nexters.ppotto.global.identifier.TermId
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "약관 동의 요청")
data class AgreeTermsRequest(
    @field:Schema(
        description = "동의한 현재 약관 ID 목록. 필수 약관이 빠지면 TERM-001",
        example = "[\"01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f\",\"01983f2a-2b3c-7d4e-9f5a-6b7c8d9e0f1a\"]",
    )
    val termIds: List<TermId>,
)
