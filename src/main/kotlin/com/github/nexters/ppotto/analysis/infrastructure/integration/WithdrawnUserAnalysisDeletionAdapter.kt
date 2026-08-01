package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.application.AnalysisWithdrawalService
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.user.application.port.WithdrawnUserAnalysisDeletionPort
import org.springframework.stereotype.Component

@Component
class WithdrawnUserAnalysisDeletionAdapter(
    private val analysisWithdrawalService: AnalysisWithdrawalService,
) : WithdrawnUserAnalysisDeletionPort {
    override fun deleteAllByUserId(userId: UserId): Unit = analysisWithdrawalService.deleteAllByUserId(userId)
}
