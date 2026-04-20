package com.bgtactician.app.data.repository

import com.bgtactician.app.data.model.BattlegroundCardMetadataCatalog
import com.bgtactician.app.data.model.BgsMinionTypesRules
import com.bgtactician.app.data.model.CardRulesCatalog
import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.Tribe

object MinionLobbyFilter {

    fun filterMinionsForLobby(
        minions: List<KeyMinion>,
        selectedTribes: Set<Tribe>,
        cardRules: CardRulesCatalog,
        cardMetadata: BattlegroundCardMetadataCatalog = BattlegroundCardMetadataCatalog(),
        selectedHeroCardId: String? = null
    ): List<KeyMinion> {
        if (selectedTribes.isEmpty() && cardRules.isEmpty() && cardMetadata.cards.isEmpty()) {
            return minions
        }
        return minions.filter { minion ->
            isMinionAllowedInLobby(
                minion = minion,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                cardMetadata = cardMetadata,
                selectedHeroCardId = selectedHeroCardId
            )
        }
    }

    fun isMinionAllowedInLobby(
        minion: KeyMinion,
        selectedTribes: Set<Tribe>,
        cardRules: CardRulesCatalog,
        cardMetadata: BattlegroundCardMetadataCatalog = BattlegroundCardMetadataCatalog(),
        selectedHeroCardId: String? = null
    ): Boolean {
        val cardId = minion.cardId ?: return true
        val rules = cardRules[cardId]?.bgsMinionTypesRules

        if (!selectedHeroCardId.isNullOrBlank()) {
            if (selectedHeroCardId in rules.orEmpty().bannedForHeroes) return false
            if (selectedHeroCardId in rules.orEmpty().alwaysAvailableForHeroes) return true
        }

        if (selectedTribes.isEmpty()) return true

        val lobbyTypes = selectedTribes.mapTo(mutableSetOf()) { it.name }
        if (rules != null) {
            if (rules.needTypesInLobby.any { it !in lobbyTypes }) return false
            if (rules.bannedWithTypesInLobby.any { it in lobbyTypes }) return false
        }

        val minionTribes = resolveMinionTribes(cardId, cardMetadata)
        if (minionTribes.isNotEmpty() && minionTribes.none { it in selectedTribes }) {
            return false
        }

        return true
    }

    private fun resolveMinionTribes(
        cardId: String,
        cardMetadata: BattlegroundCardMetadataCatalog
    ): Set<Tribe> {
        return cardMetadata.cards[cardId]
            ?.races
            .orEmpty()
            .mapNotNull(Tribe::fromMetadataRace)
            .toSet()
    }

    private fun BgsMinionTypesRules?.orEmpty(): BgsMinionTypesRules = this ?: BgsMinionTypesRules()
}
