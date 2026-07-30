package com.github.nexters.ppotto.analysis.domain

interface GeminiClassifier {
    fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification>
}
