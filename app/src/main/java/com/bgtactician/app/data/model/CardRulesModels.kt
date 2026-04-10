package com.bgtactician.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CardRuleEntry(
    val bgsMinionTypesRules: BgsMinionTypesRules? = null
)

@Serializable
data class BgsMinionTypesRules(
    val needTypesInLobby: List<String> = emptyList(),
    val bannedWithTypesInLobby: List<String> = emptyList(),
    val alwaysAvailableForHeroes: List<String> = emptyList(),
    val bannedForHeroes: List<String> = emptyList(),
    val needBoardTypes: List<String> = emptyList(),
    val needMenagerie: Boolean = false,
    val requireDivineShieldMinions: Boolean = false,
    val requireTavernTier3: Boolean = false
)

typealias CardRulesCatalog = Map<String, CardRuleEntry>
