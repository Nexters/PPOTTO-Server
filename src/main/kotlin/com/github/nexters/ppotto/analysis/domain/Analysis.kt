package com.github.nexters.ppotto.analysis.domain

import java.time.Instant
import java.util.UUID

class Analysis(
    val id: UUID,
    val userId: UUID,
    val boardId: UUID,
    val status: AnalysisStatus,
    val progress: Int,
    val failedReason: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
