package com.bgtactician.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BattlegroundCardStatsCatalog(
    val cardStats: List<BattlegroundCardStatsEntry> = emptyList(),
    val lastUpdateDate: String? = null,
    val dataPoints: Int? = null,
    val timePeriod: String? = null
)

@Serializable
data class BattlegroundCardStatsEntry(
    val cardId: String,
    val totalPlayed: Int = 0,
    val averagePlacement: Double? = null,
    val averagePlacementOther: Double? = null,
    val turnStats: List<BattlegroundCardTurnStats> = emptyList()
)

@Serializable
data class BattlegroundCardTurnStats(
    val turn: Int,
    val totalPlayed: Int = 0,
    val averagePlacement: Double? = null,
    val totalOther: Int = 0,
    val averagePlacementOther: Double? = null
)
