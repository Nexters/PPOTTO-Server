package com.github.nexters.ppotto.terms.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.terms.application.TermResult
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "현재 유효한 약관과 사용자 동의 상태")
data class TermResponse(
    @field:Schema(description = "약관 ID (uuidv7)", example = "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    val id: UUID,
    @field:Schema(description = "약관 코드", example = "TOS")
    val code: String,
    @field:Schema(description = "약관 버전", example = "1.0")
    val version: String,
    @get:Schema(description = "필수 동의 여부", example = "true")
    @get:JsonProperty("isRequired")
    val isRequired: Boolean,
    @field:Schema(description = "노션 등 외부 문서 링크", example = "https://nexters.notion.site/ppotto-tos")
    val contentUrl: String?,
    @field:Schema(
        description = "요청 사용자의 동의 여부. 인증하지 않은 요청은 항상 false",
        example = "true",
    )
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
