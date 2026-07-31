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
    fun findCurrentTerms(userId: UUID): List<TermResult> {
        val currentTerms = termRepository.findCurrentEffective(Instant.now())
        return currentTerms.withAgreementStatus(userId)
    }

    @Transactional(readOnly = true)
    fun findPendingTerms(userId: UUID): List<TermResult> = findCurrentTerms(userId).filterNot { it.agreed }

    @Transactional
    fun agree(
        userId: UUID,
        termIds: Collection<UUID>,
    ) {
        val requestedTermIds = termIds.toSet()
        val currentTerms = termRepository.findCurrentEffective(Instant.now())
        val currentTermIds = currentTerms.mapTo(mutableSetOf()) { it.id }
        val agreedTermIds = termAgreementRepository.findAgreedTermIds(userId, currentTermIds)

        validateRequiredTerms(currentTerms, agreedTermIds + requestedTermIds)
        if (!currentTermIds.containsAll(requestedTermIds)) {
            throw InvalidInputException()
        }

        termAgreementRepository.saveAll(userId, requestedTermIds)
    }

    private fun List<Term>.withAgreementStatus(userId: UUID): List<TermResult> {
        val agreedTermIds = termAgreementRepository.findAgreedTermIds(userId, map { it.id })
        return map { term -> TermResult.from(term, term.id in agreedTermIds) }
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
