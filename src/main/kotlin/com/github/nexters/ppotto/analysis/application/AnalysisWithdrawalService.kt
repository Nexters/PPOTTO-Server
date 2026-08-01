package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.PhotoStorage
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisWithdrawalRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoObjectKeys
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class AnalysisWithdrawalService(
    private val analysisWithdrawalRepository: AnalysisWithdrawalRepository,
    private val photoRepository: PhotoRepository,
    private val photoStorage: PhotoStorage,
    private val transactionTemplate: TransactionTemplate,
) {
    fun deleteAllByUserId(userId: UserId) {
        analysisWithdrawalRepository
            .findAllIdsByUserId(userId)
            .onEach { photoStorage.deleteByPrefix(PhotoObjectKeys.prefixFor(it.value)) }
            .let { analysisIds ->
                transactionTemplate.executeWithoutResult {
                    photoRepository.hardDeleteAllByAnalysisIds(analysisIds.map(AnalysisId::value))
                    analysisWithdrawalRepository.hardDeleteAllByUserId(userId)
                }
            }
    }
}
