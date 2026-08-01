package com.github.nexters.ppotto.terms.infrastructure

import com.github.nexters.ppotto.global.identifier.TermId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.records.TermAgreementsRecord
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
import com.github.nexters.ppotto.terms.domain.TermAgreement
import org.jooq.DSLContext
import org.jooq.impl.DSL.row
import org.springframework.stereotype.Repository

@Repository
class TermAgreementRepository(
    private val dslContext: DSLContext,
) {
    fun findAgreedTermIds(
        userId: UserId,
        termIds: Collection<TermId>,
    ): Set<TermId> =
        termIds
            .takeIf { it.isNotEmpty() }
            ?.let { ids ->
                dslContext
                    .select(TERM_AGREEMENTS.TERM_ID)
                    .from(TERM_AGREEMENTS)
                    .where(TERM_AGREEMENTS.USER_ID.eq(userId.value))
                    .and(TERM_AGREEMENTS.TERM_ID.`in`(ids.map(TermId::value)))
                    .fetch(TERM_AGREEMENTS.TERM_ID)
                    .filterNotNull()
                    .mapTo(mutableSetOf(), ::TermId)
            } ?: emptySet()

    fun saveAll(
        userId: UserId,
        termIds: Collection<TermId>,
    ): List<TermAgreement> =
        termIds
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { ids ->
                dslContext
                    .insertInto(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID, TERM_AGREEMENTS.TERM_ID)
                    .valuesOfRows(ids.map { termId -> row(userId.value, termId.value) })
                    .onConflict(TERM_AGREEMENTS.USER_ID, TERM_AGREEMENTS.TERM_ID)
                    .doNothing()
                    .returning()
                    .fetch()
                    .map { record -> record.toDomain() }
            } ?: emptyList()

    fun deleteAllByUserId(userId: UserId): Int =
        dslContext
            .deleteFrom(TERM_AGREEMENTS)
            .where(TERM_AGREEMENTS.USER_ID.eq(userId.value))
            .execute()

    private fun TermAgreementsRecord.toDomain() =
        TermAgreement(
            id = id!!,
            userId = UserId(userId),
            termId = TermId(termId),
            agreedAt = agreedAt!!,
        )
}
