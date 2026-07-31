package com.github.nexters.ppotto.terms.presentation.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "약관 동의 요청")
data class AgreeTermsRequest(
    @field:Schema(description = "동의한 현재 약관 ID 목록")
    val termIds: List<UUID>,
)
