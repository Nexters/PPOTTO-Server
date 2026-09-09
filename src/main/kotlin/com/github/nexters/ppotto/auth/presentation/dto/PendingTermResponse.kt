package com.github.nexters.ppotto.auth.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.nexters.ppotto.auth.domain.PendingTerm
import com.github.nexters.ppotto.global.identifier.TermId
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "로그인 후 동의가 필요한 약관")
data class PendingTermResponse(
    @get:Schema(description = "약관 ID (uuidv7)", example = "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f")
    @get:JsonProperty("id")
    val id: TermId,

    @field:Schema(description = "약관 코드", example = "TOS")
    val code: String,

    @field:Schema(description = "약관 버전", example = "1.0")
    val version: String,

    @get:Schema(description = "필수 동의 여부", example = "true")
    @get:JsonProperty("isRequired")
    val isRequired: Boolean,

    @field:Schema(description = "노션 등 외부 문서 링크", example = "https://nexters.notion.site/ppotto-tos")
    val contentUrl: String?,

    @field:Schema(description = "요청 사용자의 동의 여부", example = "false")
    val agreed: Boolean,
) {
    companion object {
        fun from(term: PendingTerm): PendingTermResponse =
            PendingTermResponse(
                id = term.id,
                code = term.code,
                version = term.version,
                isRequired = term.isRequired,
                contentUrl = term.contentUrl,
                agreed = term.agreed,
            )
    }
}
