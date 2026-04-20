package com.bgtactician.app.data.repository

import com.bgtactician.app.data.model.BattlegroundCardMetadataCatalog
import com.bgtactician.app.data.model.CardRulesCatalog
import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe

data class LiveMinionRecommendations(
    val primaryChoices: List<KeyMinion> = emptyList(),
    val secondaryChoices: List<KeyMinion> = emptyList()
)

object RealtimeMinionRecommendationEngine {

    fun recommend(
        strategy: StrategyComp?,
        selectedTribes: Set<Tribe>,
        cardRules: CardRulesCatalog,
        cardMetadata: BattlegroundCardMetadataCatalog = BattlegroundCardMetadataCatalog(),
        tavernTier: Int?,
        selectedHeroCardId: String? = null
    ): LiveMinionRecommendations {
        strategy ?: return LiveMinionRecommendations()
        val available = MinionLobbyFilter.filterMinionsForLobby(
            minions = strategy.keyMinions,
            selectedTribes = selectedTribes,
            cardRules = cardRules,
            cardMetadata = cardMetadata,
            selectedHeroCardId = selectedHeroCardId
        )
            .filter { isReachableAtTavernTier(it, tavernTier, cardRules) }
            .distinctBy { it.cardId ?: it.name }

        val primaryChoices = available
            .filter { !isGenericSupportMinion(it) }
            .filter { it.statusRaw == "CORE" || it.statusRaw == "RECOMMENDED" }
            .sortedWith(primaryComparator())
            .take(4)

        val primaryIds = primaryChoices.map { it.cardId ?: it.name }.toSet()
        val secondaryChoices = available
            .filter { (it.cardId ?: it.name) !in primaryIds }
            .filter { it.statusRaw == "ADDON" || it.statusRaw == "RECOMMENDED" || it.statusRaw == "CYCLE" || isGenericSupportMinion(it) }
            .sortedWith(secondaryComparator())
            .take(4)

        return LiveMinionRecommendations(
            primaryChoices = primaryChoices,
            secondaryChoices = secondaryChoices
        )
    }

    private fun isReachableAtTavernTier(
        minion: KeyMinion,
        tavernTier: Int?,
        cardRules: CardRulesCatalog
    ): Boolean {
        tavernTier ?: return true
        if (minion.techLevel > tavernTier) return false
        val rules = minion.cardId?.let(cardRules::get)?.bgsMinionTypesRules
        if (rules?.requireTavernTier3 == true && tavernTier < 3) return false
        return true
    }

    private fun isGenericSupportMinion(minion: KeyMinion): Boolean {
        val normalized = minion.name.lowercase()
        return normalized.contains("brann") ||
            normalized.contains("baron") ||
            normalized.contains("rivendare") ||
            normalized.contains("drakkari") ||
            normalized.contains("macaw") ||
            normalized.contains("rylak")
    }

    private fun primaryComparator(): Comparator<KeyMinion> {
        return compareBy<KeyMinion>(
            { primaryStatusRank(it.statusRaw) },
            { it.techLevel },
            { -(it.finalBoardWeight ?: 0) },
            { it.name }
        )
    }

    private fun secondaryComparator(): Comparator<KeyMinion> {
        return compareBy<KeyMinion>(
            { secondaryStatusRank(it.statusRaw, isGenericSupportMinion(it)) },
            { it.techLevel },
            { -(it.finalBoardWeight ?: 0) },
            { it.name }
        )
    }

    private fun primaryStatusRank(statusRaw: String?): Int = when (statusRaw?.uppercase()) {
        "CORE" -> 0
        "RECOMMENDED" -> 1
        "ADDON" -> 2
        "CYCLE" -> 3
        else -> 4
    }

    private fun secondaryStatusRank(statusRaw: String?, genericSupport: Boolean): Int = when {
        statusRaw?.uppercase() == "ADDON" && !genericSupport -> 0
        genericSupport -> 1
        statusRaw?.uppercase() == "RECOMMENDED" -> 2
        statusRaw?.uppercase() == "CYCLE" -> 3
        statusRaw?.uppercase() == "ADDON" -> 4
        else -> 5
    }
}
