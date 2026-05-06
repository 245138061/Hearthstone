package com.bgtactician.app.data.repository

import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.PositioningHint
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `核心随从存在缺失种族时标记为未成型`() {
        val strategy = sampleStrategy(
            id = "quilboar-smuggler",
            requiredTribes = listOf("Quilboar"),
            keyMinions = listOf(
                KeyMinion(id = 1, name = "宝石走私商", techLevel = 5, phase = "主核", statusRaw = "CORE", cardId = "Q"),
                KeyMinion(id = 2, name = "重金属双头飞龙", techLevel = 4, phase = "主核", statusRaw = "CORE", cardId = "B"),
            )
        )

        val ready = StrategyEngine.isReady(
            strategy = strategy,
            selectedTribes = setOf(Tribe.QUILBOAR, Tribe.MECH, Tribe.DEMON, Tribe.UNDEAD, Tribe.PIRATE),
            minionTribesByCardId = mapOf(
                "Q" to setOf(Tribe.QUILBOAR),
                "B" to setOf(Tribe.BEAST),
            )
        )

        assertFalse(ready)
    }

    @Test
    fun `核心随从真实种族都在当前环境时标记为成型`() {
        val strategy = sampleStrategy(
            id = "mech-ready",
            requiredTribes = listOf("Mech"),
            keyMinions = listOf(
                KeyMinion(id = 1, name = "自动装配机", techLevel = 4, phase = "主核", statusRaw = "CORE", cardId = "M"),
                KeyMinion(id = 2, name = "义肢假手", techLevel = 5, phase = "主核", statusRaw = "CORE", cardId = "U"),
            )
        )

        val ready = StrategyEngine.isReady(
            strategy = strategy,
            selectedTribes = setOf(Tribe.MECH, Tribe.UNDEAD, Tribe.DEMON, Tribe.PIRATE, Tribe.ELEMENTAL),
            minionTribesByCardId = mapOf(
                "M" to setOf(Tribe.MECH),
                "U" to setOf(Tribe.MECH, Tribe.UNDEAD),
            )
        )

        assertTrue(ready)
    }

    private fun sampleStrategy(
        id: String = "mech-demon",
        requiredTribes: List<String> = listOf("Mech", "Demon"),
        keyMinions: List<KeyMinion> = listOf(KeyMinion(id = 1, name = "随从", techLevel = 3, phase = "中期"))
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
        keyMinions = keyMinions
    )
}
