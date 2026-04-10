package com.bgtactician.app.data.repository

import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe

object StrategyEngine {

    fun filter(
        allStrategies: List<StrategyComp>,
        selectedTribes: Set<Tribe>
    ): List<StrategyComp> {
        return allStrategies
            .filter { comp ->
                comp.requiredTribes
                    .mapNotNull(Tribe::fromWireName)
                    .all(selectedTribes::contains)
            }
            .sortedWith(
                compareBy<StrategyComp> { tierRank(it.tier) }
                    .thenBy { difficultyRank(it.difficulty) }
                    .thenBy { it.name }
            )
    }

    private fun tierRank(tier: String): Int = when (tier) {
        "T0" -> 0
        "T1" -> 1
        "T2" -> 2
        else -> 3
    }

    private fun difficultyRank(difficulty: String): Int = when (difficulty) {
        "低" -> 0
        "中" -> 1
        "高" -> 2
        else -> 3
    }
}
