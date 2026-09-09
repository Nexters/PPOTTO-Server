package com.github.nexters.ppotto.terms.support

import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.terms.domain.Term
import org.jooq.DSLContext
import java.time.Instant

fun DSLContext.saveTerm(
    code: String,
    version: String = "1.0",
    effectiveAt: Instant = Instant.now().minusSeconds(60),
    isRequired: Boolean = false,
): Term {
    val saved =
        insertInto(TERMS, TERMS.CODE, TERMS.VERSION, TERMS.IS_REQUIRED, TERMS.CONTENT_URL, TERMS.EFFECTIVE_AT)
            .values(code, version, isRequired, "https://example.com/$code/$version", effectiveAt)
            .returning()
            .fetchOne()!!
    return Term(
        id = saved.id!!,
        code = saved.code,
        version = saved.version,
        isRequired = saved.isRequired!!,
        contentUrl = saved.contentUrl,
        effectiveAt = saved.effectiveAt,
        createdAt = saved.createdAt!!,
        updatedAt = saved.updatedAt!!,
    )
}
