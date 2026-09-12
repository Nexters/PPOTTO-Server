package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.application.port.PhotoStorage
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisWithdrawalRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
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
        val analysisIds = analysisWithdrawalRepository.findAllIdsByUserId(userId)
        analysisIds.forEach { photoStorage.deleteAll(it) }

        transactionTemplate.executeWithoutResult {
            photoRepository.hardDeleteAllByAnalysisIds(analysisIds)
            analysisWithdrawalRepository.hardDeleteAllByUserId(userId)
        }
    }
}
