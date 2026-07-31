package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.application.AnalysisWithdrawalService
import com.github.nexters.ppotto.user.application.port.WithdrawnUserAnalysisDeletionPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class WithdrawnUserAnalysisDeletionAdapter(
    private val analysisWithdrawalService: AnalysisWithdrawalService,
) : WithdrawnUserAnalysisDeletionPort {
    override fun deleteAllByUserId(userId: UUID): Unit = analysisWithdrawalService.deleteAllByUserId(userId)
}
