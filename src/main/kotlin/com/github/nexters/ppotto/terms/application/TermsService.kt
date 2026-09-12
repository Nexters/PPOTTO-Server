package com.github.nexters.ppotto.terms.application

import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.identifier.TermId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.terms.application.model.TermResult
import com.github.nexters.ppotto.terms.domain.Term
import com.github.nexters.ppotto.terms.domain.TermErrorCode
import com.github.nexters.ppotto.terms.infrastructure.TermAgreementRepository
import com.github.nexters.ppotto.terms.infrastructure.TermRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TermsService(
    private val termRepository: TermRepository,
    private val termAgreementRepository: TermAgreementRepository,
) {
    @Transactional(readOnly = true)
    fun findCurrentTerms(userId: UserId?): List<TermResult> {
        val currentTerms = termRepository.findCurrentEffective(Instant.now())
        if (userId == null) {
            return currentTerms.map { TermResult.from(it, false) }
        }
        return currentTerms.withAgreementStatus(userId)
    }

    @Transactional(readOnly = true)
    fun findPendingTerms(userId: UserId): List<TermResult> = findCurrentTerms(userId).filterNot { it.agreed }

    @Transactional
    fun agree(
        userId: UserId,
        termIds: Collection<TermId>,
    ) {
        val requestedTermIds = termIds.toSet()
        val currentTerms = termRepository.findCurrentEffective(Instant.now())
        val currentTermIds = currentTerms.mapTo(mutableSetOf()) { it.id }
        if (!currentTermIds.containsAll(requestedTermIds)) {
            throw InvalidInputException()
        }

        val agreedTermIds = termAgreementRepository.findAgreedTermIds(userId, currentTermIds)
        validateRequiredTerms(currentTerms, agreedTermIds + requestedTermIds)

        termAgreementRepository.saveAll(userId, requestedTermIds)
    }

    fun deleteAgreements(userId: UserId) {
        termAgreementRepository.deleteAllByUserId(userId)
    }

    private fun List<Term>.withAgreementStatus(userId: UserId): List<TermResult> =
        termAgreementRepository
            .findAgreedTermIds(userId, map { it.id })
            .let { agreedTermIds -> map { term -> TermResult.from(term, term.id in agreedTermIds) } }

    private fun validateRequiredTerms(
        currentTerms: List<Term>,
        agreedTermIds: Set<TermId>,
    ) {
        currentTerms.takeIf { terms -> terms.none { it.isRequired && it.id !in agreedTermIds } }
            ?: throw InvalidInputException(TermErrorCode.REQUIRED_TERMS_MISSING)
    }
}
