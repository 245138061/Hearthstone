package com.bgtactician.app.data.repository

import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.PositioningHint
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyEngineTest {

    @Test
    fun `未选满五个种族时不返回流派`() {
        val result = StrategyEngine.filter(
            allStrategies = listOf(sampleStrategy()),
            selectedTribes = setOf(Tribe.MECH, Tribe.DEMON, Tribe.UNDEAD)
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `选满五个且包含需求种族时返回匹配流派`() {
        val result = StrategyEngine.filter(
            allStrategies = listOf(sampleStrategy(), sampleStrategy(id = "other", requiredTribes = listOf("Dragon"))),
            selectedTribes = setOf(
                Tribe.MECH,
                Tribe.DEMON,
                Tribe.UNDEAD,
                Tribe.PIRATE,
                Tribe.ELEMENTAL
            )
        )

        assertEquals(listOf("mech-demon"), result.map(StrategyComp::id))
    }

    private fun sampleStrategy(
        id: String = "mech-demon",
        requiredTribes: List<String> = listOf("Mech", "Demon")
    ) = StrategyComp(
        id = id,
        name = id,
        tier = "T1",
        difficulty = "中",
        requiredTribes = requiredTribes,
        overview = "overview",
        earlyStrategy = "early",
        lateStrategy = "late",
        upgradeTurns = listOf("3本"),
        positioningHints = listOf(PositioningHint(slot = 1, label = "前排", note = "站前面")),
        keyMinions = listOf(KeyMinion(id = 1, name = "随从", techLevel = 3, phase = "中期"))
    )
}
