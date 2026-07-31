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
    fun findCurrentTerms(userId: UUID?): List<TermResult> =
        termRepository.findCurrentEffective(Instant.now()).let { currentTerms ->
            userId?.let { currentTerms.withAgreementStatus(it) }
                ?: currentTerms.map { TermResult.from(it, false) }
        }

    @Transactional(readOnly = true)
    fun findPendingTerms(userId: UUID): List<TermResult> = findCurrentTerms(userId).filterNot { it.agreed }

    @Transactional
    fun agree(
        userId: UUID,
        termIds: Collection<UUID>,
    ) = termIds
        .toSet()
        .also { requestedTermIds ->
            termRepository.findCurrentEffective(Instant.now()).let { currentTerms ->
                currentTerms.mapTo(mutableSetOf()) { it.id }.let { currentTermIds ->
                    termAgreementRepository
                        .findAgreedTermIds(userId, currentTermIds)
                        .also { validateRequiredTerms(currentTerms, it + requestedTermIds) }
                        .let {
                            currentTermIds
                                .takeIf { ids -> ids.containsAll(requestedTermIds) }
                                ?: throw InvalidInputException()
                        }
                }
            }
        }.let { termAgreementRepository.saveAll(userId, it) }

    @Transactional
    fun deleteAgreements(userId: UUID) {
        termAgreementRepository.deleteAllByUserId(userId)
    }

    private fun List<Term>.withAgreementStatus(userId: UUID): List<TermResult> =
        termAgreementRepository
            .findAgreedTermIds(userId, map { it.id })
            .let { agreedTermIds ->
                map { term -> TermResult.from(term, term.id in agreedTermIds) }
            }

    private fun validateRequiredTerms(
        currentTerms: List<Term>,
        agreedTermIds: Set<UUID>,
    ) {
        currentTerms
            .none { it.isRequired && it.id !in agreedTermIds }
            .takeIf { it }
            ?: throw InvalidInputException(TermErrorCode.REQUIRED_TERMS_MISSING)
    }
}
