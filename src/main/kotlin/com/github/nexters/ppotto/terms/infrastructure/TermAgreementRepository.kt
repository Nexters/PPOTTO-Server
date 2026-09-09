package com.github.nexters.ppotto.terms.infrastructure

import com.github.nexters.ppotto.global.identifier.TermId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.jooq.tables.references.TERM_AGREEMENTS
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
    ): Set<TermId> {
        if (termIds.isEmpty()) {
            return emptySet()
        }
        return dslContext
            .select(TERM_AGREEMENTS.TERM_ID)
            .from(TERM_AGREEMENTS)
            .where(TERM_AGREEMENTS.USER_ID.eq(userId))
            .and(TERM_AGREEMENTS.TERM_ID.`in`(termIds))
            .fetch(TERM_AGREEMENTS.TERM_ID)
            .filterNotNull()
            .toSet()
    }

    fun saveAll(
        userId: UserId,
        termIds: Collection<TermId>,
    ): Int {
        val distinctTermIds = termIds.distinct()
        if (distinctTermIds.isEmpty()) {
            return 0
        }
        return dslContext
            .insertInto(TERM_AGREEMENTS, TERM_AGREEMENTS.USER_ID, TERM_AGREEMENTS.TERM_ID)
            .valuesOfRows(distinctTermIds.map { termId -> row(userId, termId) })
            .onConflict(TERM_AGREEMENTS.USER_ID, TERM_AGREEMENTS.TERM_ID)
            .doNothing()
            .execute()
    }

    fun deleteAllByUserId(userId: UserId): Int =
        dslContext
            .deleteFrom(TERM_AGREEMENTS)
            .where(TERM_AGREEMENTS.USER_ID.eq(userId))
            .execute()
}
