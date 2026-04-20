package com.bgtactician.app.data.model

data class AutoDetectDebugInfo(
    val recognizedTribesLabel: String? = null,
    val rawText: String? = null,
    val tavernTier: Int? = null,
    val tavernTierLabel: String? = null,
    val aiSourceLabel: String? = null,
    val aiModelLabel: String? = null,
    val aiRequestId: String? = null,
    val aiScreenTypeLabel: String? = null,
    val aiHeroesLabel: String? = null,
    val roiRectLabel: String? = null,
    val aiSummaryLabel: String? = null,
    val viewportLabel: String? = null,
    val headerLabel: String? = null,
    val latestDumpPath: String? = null,
    val lastUpdatedLabel: String? = null
)
