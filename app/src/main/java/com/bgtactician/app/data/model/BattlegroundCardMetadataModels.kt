package com.bgtactician.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BattlegroundCardMetadataCatalog(
    val version: String = "",
    @SerialName("generated_at")
    val generatedAt: String? = null,
    val source: BattlegroundCardMetadataSource? = null,
    @SerialName("race_tags")
    val raceTags: Map<String, Int> = emptyMap(),
    val summary: BattlegroundCardMetadataSummary? = null,
    val cards: Map<String, BattlegroundCardMetadataEntry> = emptyMap()
)

@Serializable
data class BattlegroundCardMetadataSource(
    @SerialName("cards_en")
    val cardsEn: String? = null,
    @SerialName("cards_zh")
    val cardsZh: String? = null
)

@Serializable
data class BattlegroundCardMetadataSummary(
    @SerialName("card_count")
    val cardCount: Int = 0,
    @SerialName("type_counts")
    val typeCounts: Map<String, Int> = emptyMap()
)

@Serializable
data class BattlegroundCardMetadataEntry(
    @SerialName("dbf_id")
    val dbfId: Int? = null,
    val name: String = "",
    @SerialName("localized_name")
    val localizedName: String? = null,
    val type: String = "",
    @SerialName("tech_level")
    val techLevel: Int? = null,
    val races: List<String> = emptyList(),
    @SerialName("spell_school")
    val spellSchool: String? = null,
    @SerialName("associated_races")
    val associatedRaces: List<String> = emptyList(),
    val text: String? = null,
    @SerialName("localized_text")
    val localizedText: String? = null,
    val mechanics: List<String> = emptyList(),
    @SerialName("is_pool_minion")
    val isPoolMinion: Boolean = false,
    @SerialName("is_pool_spell")
    val isPoolSpell: Boolean = false,
    @SerialName("premium_card_id")
    val premiumCardId: String? = null,
    @SerialName("normal_card_id")
    val normalCardId: String? = null,
    @SerialName("related_card_id")
    val relatedCardId: String? = null
)
