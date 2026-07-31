package com.github.nexters.ppotto.terms.infrastructure

import com.github.nexters.ppotto.jooq.tables.records.TermsRecord
import com.github.nexters.ppotto.jooq.tables.references.TERMS
import com.github.nexters.ppotto.terms.domain.Term
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class TermRepository(
    private val dslContext: DSLContext,
) {
    fun findCurrentEffective(at: Instant): List<Term> =
        dslContext
            .selectFrom(TERMS)
            .where(TERMS.EFFECTIVE_AT.le(at))
            .orderBy(TERMS.CODE, TERMS.EFFECTIVE_AT.desc(), TERMS.ID.desc())
            .fetch()
            .map { it.toDomain() }
            .distinctBy { it.code }

    private fun TermsRecord.toDomain() =
        Term(
            id = id!!,
            code = code,
            version = version,
            isRequired = isRequired!!,
            contentUrl = contentUrl,
            effectiveAt = effectiveAt,
            createdAt = createdAt!!,
            updatedAt = updatedAt!!,
        )
}
