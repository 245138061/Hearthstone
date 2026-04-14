package com.bgtactician.app.data.repository

import com.bgtactician.app.data.model.BattlegroundCardStatsCatalog
import com.bgtactician.app.data.model.BattlegroundCardStatsEntry
import com.bgtactician.app.data.model.BattlegroundCardTurnStats
import com.bgtactician.app.data.model.BattlegroundHeroNameEntry
import com.bgtactician.app.data.model.BattlegroundHeroNameIndex
import com.bgtactician.app.data.model.BattlegroundHeroStatsCatalog
import com.bgtactician.app.data.model.BattlegroundHeroStatsEntry
import com.bgtactician.app.data.model.BattlegroundHeroTribeStats
import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.PositioningHint
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroSelectionRecommendationEngineTest {

    @Test
    fun `card stats produce concrete pivot hint and fallback`() {
        val result = HeroSelectionRecommendationEngine.resolveRecognizedHeroes(
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(
                    slot = 0,
                    heroCardId = "TB_BaconShop_HERO_53",
                    name = "瓦托格尔女王"
                )
            ),
            heroStatsCatalog = heroStatsCatalog(),
            cardStatsCatalog = BattlegroundCardStatsCatalog(
                cardStats = listOf(
                    cardStats("DRAGON_CORE_A", 90_000, 3.2, 4.1, stableTurn = 5),
                    cardStats("DRAGON_CORE_B", 88_000, 3.3, 4.0, stableTurn = 5),
                    cardStats("DRAGON_CORE_C", 75_000, 3.5, 4.0, stableTurn = 6),
                    cardStats("DRAGON_PIVOT_A", 70_000, 3.45, 3.95, stableTurn = 4),
                    cardStats("DRAGON_PIVOT_B", 66_000, 3.52, 3.96, stableTurn = 4)
                )
            ),
            heroNameIndex = heroNameIndex(),
            selectedTribes = setOf(Tribe.DRAGON, Tribe.MECH, Tribe.DEMON, Tribe.BEAST, Tribe.MURLOC),
            allStrategies = listOf(
                strategy(
                    id = "dragon_main",
                    name = "主龙流",
                    tier = "T1",
                    requiredTribes = listOf(Tribe.DRAGON.wireName),
                    whenToCommit = "龙核两件套",
                    keyMinions = listOf(
                        keyMinion("DRAGON_CORE_A", "诗心龙甲"),
                        keyMinion("DRAGON_CORE_B", "龙群先锋"),
                        keyMinion("DRAGON_CORE_C", "暮光巢母", phase = "补强")
                    )
                ),
                strategy(
                    id = "dragon_fallback",
                    name = "转向龙流",
                    tier = "T1",
                    requiredTribes = listOf(Tribe.DRAGON.wireName),
                    whenToCommit = "转向龙核",
                    keyMinions = listOf(
                        keyMinion("DRAGON_PIVOT_A", "迷雾幼龙"),
                        keyMinion("DRAGON_PIVOT_B", "雏龙执行者")
                    )
                )
            )
        )

        val hero = result.single()
        assertEquals("主龙流", hero.recommendation?.recommendedCompName)
        assertEquals("转向龙流", hero.recommendation?.fallbackCompName)
        assertTrue(hero.recommendation?.pivotHint?.contains("到8回合还没见到诗心龙甲 / 龙群先锋") == true)
        assertTrue(hero.recommendation?.pivotHint?.contains("转向龙流") == true)
    }

    @Test
    fun `missing card stats keeps recommendation available`() {
        val result = HeroSelectionRecommendationEngine.resolveRecognizedHeroes(
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(
                    slot = 1,
                    heroCardId = "TB_BaconShop_HERO_53",
                    name = "瓦托格尔女王"
                )
            ),
            heroStatsCatalog = heroStatsCatalog(),
            cardStatsCatalog = BattlegroundCardStatsCatalog(),
            heroNameIndex = heroNameIndex(),
            selectedTribes = setOf(Tribe.DRAGON, Tribe.MECH, Tribe.DEMON, Tribe.BEAST, Tribe.MURLOC),
            allStrategies = listOf(
                strategy(
                    id = "dragon_main",
                    name = "主龙流",
                    tier = "T1",
                    requiredTribes = listOf(Tribe.DRAGON.wireName),
                    whenToCommit = "龙核两件套",
                    keyMinions = listOf(
                        keyMinion("DRAGON_CORE_A", "诗心龙甲"),
                        keyMinion("DRAGON_CORE_B", "龙群先锋")
                    )
                )
            )
        )

        val hero = result.single()
        assertEquals("主龙流", hero.recommendation?.recommendedCompName)
        assertNotNull(hero.recommendation?.reason)
    }

    @Test
    fun `pivot hint prefers main core over generic support pieces`() {
        val result = HeroSelectionRecommendationEngine.resolveRecognizedHeroes(
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(
                    slot = 2,
                    heroCardId = "TB_BaconShop_HERO_53",
                    name = "伊瑟拉"
                )
            ),
            heroStatsCatalog = heroStatsCatalog(),
            cardStatsCatalog = BattlegroundCardStatsCatalog(
                cardStats = listOf(
                    cardStats("DRAGON_GENERIC", 200_000, 3.0, 4.2, stableTurn = 4),
                    cardStats("DRAGON_CORE_A", 90_000, 3.2, 4.1, stableTurn = 5),
                    cardStats("DRAGON_CORE_B", 88_000, 3.3, 4.0, stableTurn = 5),
                    cardStats("DRAGON_PIVOT_A", 70_000, 3.45, 3.95, stableTurn = 4),
                    cardStats("DRAGON_PIVOT_B", 66_000, 3.52, 3.96, stableTurn = 4)
                )
            ),
            heroNameIndex = heroNameIndex(),
            selectedTribes = setOf(Tribe.DRAGON, Tribe.MECH, Tribe.DEMON, Tribe.BEAST, Tribe.MURLOC),
            allStrategies = listOf(
                strategy(
                    id = "dragon_main",
                    name = "主龙流",
                    tier = "T1",
                    requiredTribes = listOf(Tribe.DRAGON.wireName),
                    whenToCommit = "龙核两件套",
                    keyMinions = listOf(
                        keyMinion("DRAGON_GENERIC", "提图斯·瑞文戴尔"),
                        keyMinion("DRAGON_CORE_A", "诗心龙甲"),
                        keyMinion("DRAGON_CORE_B", "龙群先锋")
                    )
                ),
                strategy(
                    id = "dragon_fallback",
                    name = "转向龙流",
                    tier = "T1",
                    requiredTribes = listOf(Tribe.DRAGON.wireName),
                    whenToCommit = "转向龙核",
                    keyMinions = listOf(
                        keyMinion("DRAGON_PIVOT_A", "迷雾幼龙"),
                        keyMinion("DRAGON_PIVOT_B", "雏龙执行者")
                    )
                )
            )
        )

        val hero = result.single()
        assertTrue(hero.recommendation?.pivotHint?.contains("诗心龙甲 / 龙群先锋") == true)
        assertTrue(hero.recommendation?.pivotHint?.contains("提图斯·瑞文戴尔") == false)
    }

    private fun heroStatsCatalog() = BattlegroundHeroStatsCatalog(
        heroStats = listOf(
            BattlegroundHeroStatsEntry(
                heroCardId = "TB_BaconShop_HERO_53",
                averagePosition = 3.9,
                dataPoints = 12_000,
                tribeStats = listOf(
                    BattlegroundHeroTribeStats(
                        tribe = 24,
                        averagePosition = 3.8,
                        impactAveragePositionVsMissingTribe = -0.15
                    )
                )
            )
        )
    )

    private fun heroNameIndex() = BattlegroundHeroNameIndex(
        version = "test",
        heroes = listOf(
            BattlegroundHeroNameEntry(
                heroCardId = "TB_BaconShop_HERO_53",
                name = "Queen Wagtoggle",
                localizedName = "瓦托格尔女王",
                aliases = listOf("瓦托格尔女王")
            )
        )
    )

    private fun strategy(
        id: String,
        name: String,
        tier: String,
        requiredTribes: List<String>,
        whenToCommit: String,
        keyMinions: List<KeyMinion>
    ) = StrategyComp(
        id = id,
        name = name,
        tier = tier,
        difficulty = "中",
        requiredTribes = requiredTribes,
        whenToCommit = whenToCommit,
        overview = "test",
        earlyStrategy = "test",
        lateStrategy = "test",
        upgradeTurns = listOf("5本"),
        positioningHints = listOf(PositioningHint(slot = 1, label = "核心", note = "test")),
        keyMinions = keyMinions
    )

    private fun keyMinion(cardId: String, name: String, phase: String = "主核") = KeyMinion(
        id = cardId.hashCode(),
        name = name,
        star = 5,
        phase = phase,
        cardId = cardId
    )

    private fun cardStats(
        cardId: String,
        totalPlayed: Int,
        averagePlacement: Double,
        averagePlacementOther: Double,
        stableTurn: Int
    ) = BattlegroundCardStatsEntry(
        cardId = cardId,
        totalPlayed = totalPlayed,
        averagePlacement = averagePlacement,
        averagePlacementOther = averagePlacementOther,
        turnStats = listOf(
            BattlegroundCardTurnStats(
                turn = stableTurn,
                totalPlayed = 1_200,
                averagePlacement = averagePlacement,
                averagePlacementOther = averagePlacementOther
            )
        )
    )
}
