package com.github.nexters.ppotto.terms.application

import com.github.nexters.ppotto.terms.domain.Term
import java.util.UUID

data class TermResult(
    val id: UUID,
    val code: String,
    val version: String,
    val isRequired: Boolean,
    val contentUrl: String?,
    val agreed: Boolean,
) {
    companion object {
        fun from(
            term: Term,
            agreed: Boolean,
        ) = TermResult(
            id = term.id,
            code = term.code,
            version = term.version,
            isRequired = term.isRequired,
            contentUrl = term.contentUrl,
            agreed = agreed,
        )
    }
}
