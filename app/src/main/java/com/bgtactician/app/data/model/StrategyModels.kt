package com.bgtactician.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StrategyCatalog(
    val version: String,
    val comps: List<StrategyComp>
)

@Serializable
data class StrategyComp(
    val id: String,
    val name: String,
    val tier: String,
    val difficulty: String,
    @SerialName("power_level")
    val powerLevel: String? = null,
    @SerialName("required_tribes")
    val requiredTribes: List<String>,
    @SerialName("allowed_anomalies")
    val allowedAnomalies: List<String> = emptyList(),
    @SerialName("recommended_mode")
    val recommendedMode: String = "BOTH",
    @SerialName("when_to_commit")
    val whenToCommit: String? = null,
    @SerialName("source_patch_number")
    val sourcePatchNumber: Int? = null,
    val overview: String,
    @SerialName("early_strategy")
    val earlyStrategy: String,
    @SerialName("late_strategy")
    val lateStrategy: String,
    @SerialName("upgrade_turns")
    val upgradeTurns: List<String>,
    @SerialName("positioning_hints")
    val positioningHints: List<PositioningHint>,
    @SerialName("key_minions")
    val keyMinions: List<KeyMinion>
)

@Serializable
data class KeyMinion(
    val id: Int,
    val name: String,
    val star: Int,
    val phase: String,
    @SerialName("status_raw")
    val statusRaw: String? = null,
    @SerialName("final_board_weight")
    val finalBoardWeight: Int? = null,
    @SerialName("card_id")
    val cardId: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("image_asset")
    val imageAsset: String? = null
)

@Serializable
data class PositioningHint(
    val slot: Int,
    val label: String,
    val note: String
)

enum class Tribe(val wireName: String, val label: String, val shortLabel: String) {
    BEAST("Beast", "野兽", "兽"),
    DEMON("Demon", "恶魔", "魔"),
    DRAGON("Dragon", "龙", "龙"),
    ELEMENTAL("Elemental", "元素", "元"),
    MECH("Mech", "机械", "机"),
    MURLOC("Murloc", "鱼人", "鱼"),
    NAGA("Naga", "娜迦", "娜"),
    PIRATE("Pirate", "海盗", "盗"),
    QUILBOAR("Quilboar", "野猪人", "猪"),
    UNDEAD("Undead", "亡灵", "亡");

    companion object {
        fun fromWireName(value: String): Tribe? = entries.firstOrNull { it.wireName == value }
    }
}
