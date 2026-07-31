package com.github.nexters.ppotto.terms.presentation.dto

import com.github.nexters.ppotto.terms.application.TermResult
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "현재 유효한 약관과 사용자 동의 상태")
data class TermResponse(
    val id: UUID,
    val code: String,
    val version: String,
    val isRequired: Boolean,
    val contentUrl: String?,
    val agreed: Boolean,
) {
    companion object {
        fun from(result: TermResult) =
            TermResponse(
                id = result.id,
                code = result.code,
                version = result.version,
                isRequired = result.isRequired,
                contentUrl = result.contentUrl,
                agreed = result.agreed,
            )
    }
}
