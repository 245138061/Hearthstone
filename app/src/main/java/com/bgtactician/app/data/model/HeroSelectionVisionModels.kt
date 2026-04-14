package com.bgtactician.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VisionScreenType {
    @SerialName("unknown")
    UNKNOWN,

    @SerialName("non_target")
    NON_TARGET,

    @SerialName("hero_selection")
    HERO_SELECTION
}

@Serializable
enum class VisionTribe(val wireName: String, val label: String) {
    BEAST("Beast", "野兽"),
    DEMON("Demon", "恶魔"),
    DRAGON("Dragon", "龙"),
    ELEMENTAL("Elemental", "元素"),
    MECH("Mech", "机械"),
    MURLOC("Murloc", "鱼人"),
    NAGA("Naga", "娜迦"),
    PIRATE("Pirate", "海盗"),
    QUILBOAR("Quilboar", "野猪人"),
    UNDEAD("Undead", "亡灵");

    fun toDomain(): Tribe = when (this) {
        BEAST -> Tribe.BEAST
        DEMON -> Tribe.DEMON
        DRAGON -> Tribe.DRAGON
        ELEMENTAL -> Tribe.ELEMENTAL
        MECH -> Tribe.MECH
        MURLOC -> Tribe.MURLOC
        NAGA -> Tribe.NAGA
        PIRATE -> Tribe.PIRATE
        QUILBOAR -> Tribe.QUILBOAR
        UNDEAD -> Tribe.UNDEAD
    }

    companion object {
        fun fromWireName(value: String): VisionTribe? = entries.firstOrNull { it.wireName == value }
    }
}

@Serializable
data class HeroSelectionVisionHeroOption(
    val slot: Int,
    val name: String? = null,
    @SerialName("hero_card_id")
    val heroCardId: String? = null,
    val locked: Boolean = false,
    val armor: Int? = null,
    val confidence: Float? = null
)

@Serializable
data class HeroSelectionVisionResult(
    @SerialName("screen_type")
    val screenType: VisionScreenType = VisionScreenType.UNKNOWN,
    @SerialName("available_tribes")
    val availableTribes: List<VisionTribe> = emptyList(),
    @SerialName("hero_options")
    val heroOptions: List<HeroSelectionVisionHeroOption> = emptyList(),
    val confidence: Float? = null,
    @SerialName("model_name")
    val modelName: String? = null,
    @SerialName("request_id")
    val requestId: String? = null,
    @SerialName("raw_summary")
    val rawSummary: String? = null
) {
    val isHeroSelection: Boolean
        get() = screenType == VisionScreenType.HERO_SELECTION

    val selectableHeroOptions: List<HeroSelectionVisionHeroOption>
        get() = heroOptions.filterNot { it.locked }

    fun isStableSelection(): Boolean {
        val distinctTribes = availableTribes.distinct()
        return isHeroSelection && distinctTribes.size == 5 && selectableHeroOptions.isNotEmpty()
    }

    fun toDomainTribes(): Set<Tribe> = availableTribes.mapTo(linkedSetOf()) { it.toDomain() }
}
