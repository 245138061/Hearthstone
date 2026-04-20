package com.bgtactician.app.data.repository

import com.bgtactician.app.data.model.BattlegroundCardMetadataCatalog
import com.bgtactician.app.data.model.BattlegroundCardMetadataEntry
import com.bgtactician.app.data.model.BgsMinionTypesRules
import com.bgtactician.app.data.model.CardRuleEntry
import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeMinionRecommendationEngineTest {

    @Test
    fun `recommend returns primary and secondary minions for current tavern tier`() {
        val strategy = strategy(
            keyMinions = listOf(
                minion(name = "Core Engine", techLevel = 2, statusRaw = "CORE", cardId = "A"),
                minion(name = "Recommended Piece", techLevel = 3, statusRaw = "RECOMMENDED", cardId = "B"),
                minion(name = "Addon Piece", techLevel = 3, statusRaw = "ADDON", cardId = "C"),
                minion(name = "Brann Bronzebeard", techLevel = 2, statusRaw = "ADDON", cardId = "D"),
                minion(name = "Late Piece", techLevel = 5, statusRaw = "CORE", cardId = "E")
            )
        )

        val result = RealtimeMinionRecommendationEngine.recommend(
            strategy = strategy,
            selectedTribes = setOf(Tribe.DEMON, Tribe.MECH, Tribe.NAGA, Tribe.BEAST, Tribe.UNDEAD),
            cardRules = emptyMap(),
            tavernTier = 3
        )

        assertEquals(listOf("Core Engine", "Recommended Piece"), result.primaryChoices.map(KeyMinion::name))
        assertEquals(listOf("Addon Piece", "Brann Bronzebeard"), result.secondaryChoices.map(KeyMinion::name))
    }

    @Test
    fun `recommend respects lobby and tavern tier restrictions`() {
        val strategy = strategy(
            keyMinions = listOf(
                minion(name = "Beast Core", techLevel = 2, statusRaw = "CORE", cardId = "BEAST_CORE"),
                minion(name = "Tier Three Tool", techLevel = 2, statusRaw = "RECOMMENDED", cardId = "T3_ONLY"),
                minion(name = "Fallback Addon", techLevel = 2, statusRaw = "ADDON", cardId = "ADDON")
            )
        )
        val cardRules = mapOf(
            "BEAST_CORE" to CardRuleEntry(
                bgsMinionTypesRules = BgsMinionTypesRules(
                    needTypesInLobby = listOf("BEAST")
                )
            ),
            "T3_ONLY" to CardRuleEntry(
                bgsMinionTypesRules = BgsMinionTypesRules(
                    requireTavernTier3 = true
                )
            )
        )

        val result = RealtimeMinionRecommendationEngine.recommend(
            strategy = strategy,
            selectedTribes = setOf(Tribe.DEMON, Tribe.MECH, Tribe.NAGA, Tribe.DRAGON, Tribe.UNDEAD),
            cardRules = cardRules,
            tavernTier = 2
        )

        assertEquals(emptyList<String>(), result.primaryChoices.map(KeyMinion::name))
        assertEquals(listOf("Fallback Addon"), result.secondaryChoices.map(KeyMinion::name))
    }

    @Test
    fun `recommend respects hero specific bans and overrides`() {
        val strategy = strategy(
            keyMinions = listOf(
                minion(name = "Hero Locked Core", techLevel = 2, statusRaw = "CORE", cardId = "LOCKED"),
                minion(name = "Hero Exclusive Core", techLevel = 2, statusRaw = "CORE", cardId = "EXCLUSIVE"),
                minion(name = "Open Addon", techLevel = 2, statusRaw = "ADDON", cardId = "ADDON")
            )
        )
        val cardRules = mapOf(
            "LOCKED" to CardRuleEntry(
                bgsMinionTypesRules = BgsMinionTypesRules(
                    bannedForHeroes = listOf("HERO_A")
                )
            ),
            "EXCLUSIVE" to CardRuleEntry(
                bgsMinionTypesRules = BgsMinionTypesRules(
                    needTypesInLobby = listOf("BEAST"),
                    alwaysAvailableForHeroes = listOf("HERO_A")
                )
            )
        )

        val result = RealtimeMinionRecommendationEngine.recommend(
            strategy = strategy,
            selectedTribes = setOf(Tribe.DEMON, Tribe.MECH, Tribe.NAGA, Tribe.DRAGON, Tribe.UNDEAD),
            cardRules = cardRules,
            tavernTier = 2,
            selectedHeroCardId = "HERO_A"
        )

        assertEquals(listOf("Hero Exclusive Core"), result.primaryChoices.map(KeyMinion::name))
        assertEquals(listOf("Open Addon"), result.secondaryChoices.map(KeyMinion::name))
    }

    @Test
    fun `recommend filters tribe minions by card metadata when tribe is absent`() {
        val strategy = strategy(
            keyMinions = listOf(
                minion(name = "Open Core", techLevel = 2, statusRaw = "CORE", cardId = "CORE"),
                minion(name = "Beast Addon", techLevel = 4, statusRaw = "ADDON", cardId = "BEAST_ADDON"),
                minion(name = "Brann Bronzebeard", techLevel = 5, statusRaw = "ADDON", cardId = "BRANN")
            )
        )
        val cardMetadata = BattlegroundCardMetadataCatalog(
            cards = mapOf(
                "CORE" to BattlegroundCardMetadataEntry(
                    name = "Open Core",
                    races = listOf("DEMON")
                ),
                "BEAST_ADDON" to BattlegroundCardMetadataEntry(
                    name = "Beast Addon",
                    races = listOf("BEAST")
                ),
                "BRANN" to BattlegroundCardMetadataEntry(
                    name = "Brann Bronzebeard",
                    races = emptyList()
                )
            )
        )

        val result = RealtimeMinionRecommendationEngine.recommend(
            strategy = strategy,
            selectedTribes = setOf(Tribe.DEMON, Tribe.MECH, Tribe.NAGA, Tribe.DRAGON, Tribe.UNDEAD),
            cardRules = emptyMap(),
            cardMetadata = cardMetadata,
            tavernTier = 5
        )

        assertEquals(listOf("Open Core"), result.primaryChoices.map(KeyMinion::name))
        assertEquals(listOf("Brann Bronzebeard"), result.secondaryChoices.map(KeyMinion::name))
    }

    private fun strategy(keyMinions: List<KeyMinion>) = StrategyComp(
        id = "test",
        name = "Test Strategy",
        tier = "T1",
        difficulty = "Normal",
        requiredTribes = emptyList(),
        overview = "",
        earlyStrategy = "",
        lateStrategy = "",
        upgradeTurns = emptyList(),
        positioningHints = emptyList(),
        keyMinions = keyMinions
    )

    private fun minion(
        name: String,
        techLevel: Int,
        statusRaw: String,
        cardId: String
    ) = KeyMinion(
        id = techLevel,
        name = name,
        techLevel = techLevel,
        phase = "",
        statusRaw = statusRaw,
        finalBoardWeight = 100 - techLevel,
        cardId = cardId
    )
}
