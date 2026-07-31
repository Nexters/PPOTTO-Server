package com.github.nexters.ppotto.terms.infrastructure

import com.github.nexters.ppotto.jooq.tables.records.TermAgreementsRecord
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.terms.domain.TermAgreement
import org.jooq.DSLContext
import org.jooq.impl.DSL.row
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class TermAgreementRepository(
    private val dslContext: DSLContext,
) {
    fun findAgreedTermIds(
        userId: UUID,
        termIds: Collection<UUID>,
    ): Set<UUID> =
        termIds
            .takeIf { it.isNotEmpty() }
            ?.let {
                dslContext
                    .select(TERM_AGREEMENTS.TERM_ID)
                    .from(TERM_AGREEMENTS)
                    .where(TERM_AGREEMENTS.USER_ID.eq(userId))
                    .and(TERM_AGREEMENTS.TERM_ID.`in`(it))
                    .fetch(TERM_AGREEMENTS.TERM_ID)
                    .filterNotNull()
                    .toSet()
            } ?: emptySet()

    fun saveAll(
        userId: UUID,
        termIds: Collection<UUID>,
    ): List<TermAgreement> =
        termIds
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let {
                dslContext
                    .insertInto(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID, TERM_AGREEMENTS.TERM_ID)
                    .valuesOfRows(it.map { termId -> row(userId, termId) })
                    .onConflict(TERM_AGREEMENTS.USER_ID, TERM_AGREEMENTS.TERM_ID)
                    .doNothing()
                    .returning()
                    .fetch()
                    .map { record -> record.toDomain() }
            } ?: emptyList()

    private fun TermAgreementsRecord.toDomain() =
        TermAgreement(
            id = id!!,
            userId = userId,
            termId = termId,
            agreedAt = agreedAt!!,
        )
}
