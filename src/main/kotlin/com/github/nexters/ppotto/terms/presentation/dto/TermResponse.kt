package com.github.nexters.ppotto.terms.presentation.dto

import com.github.nexters.ppotto.terms.application.TermResult
import java.util.UUID

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
