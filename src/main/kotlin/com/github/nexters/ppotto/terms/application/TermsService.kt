package com.github.nexters.ppotto.terms.application

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.terms.domain.Term
import com.github.nexters.ppotto.terms.domain.TermErrorCode
import com.github.nexters.ppotto.terms.infrastructure.TermAgreementRepository
import com.github.nexters.ppotto.terms.infrastructure.TermRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class TermsService(
    private val termRepository: TermRepository,
    private val termAgreementRepository: TermAgreementRepository,
) {
    @Transactional(readOnly = true)
    fun findCurrentTerms(userId: UUID): List<TermResult> =
        termRepository
            .findCurrentEffective(Instant.now())
            .withAgreementStatus(userId)

    @Transactional(readOnly = true)
    fun findPendingTerms(userId: UUID): List<TermResult> = findCurrentTerms(userId).filterNot { it.agreed }

    @Transactional
    fun agree(
        userId: UUID,
        termIds: Collection<UUID>,
    ) {
        val requestedTermIds = termIds.toSet()
        val currentTerms = termRepository.findCurrentEffective(Instant.now())
        val currentTermIds = currentTerms.map(Term::id).toSet()
        val agreedTermIds = termAgreementRepository.findAgreedTermIds(userId, currentTermIds)

        validateRequiredTerms(currentTerms, agreedTermIds + requestedTermIds)
        requestedTermIds
            .takeIf(currentTermIds::containsAll)
            ?.let { termAgreementRepository.saveAll(userId, it) }
            ?: throw InvalidInputException()
    }

    private fun List<Term>.withAgreementStatus(userId: UUID): List<TermResult> =
        termAgreementRepository
            .findAgreedTermIds(userId, map(Term::id))
            .let { agreedTermIds ->
                map { term -> TermResult.from(term, term.id in agreedTermIds) }
            }

    private fun validateRequiredTerms(
        currentTerms: List<Term>,
        agreedTermIds: Set<UUID>,
    ) {
        if (currentTerms.any { it.isRequired && it.id !in agreedTermIds }) {
            throw InvalidInputException(TermErrorCode.REQUIRED_TERMS_MISSING)
        }
    }
}
