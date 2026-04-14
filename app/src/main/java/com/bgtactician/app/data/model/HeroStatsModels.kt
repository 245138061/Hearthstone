package com.bgtactician.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BattlegroundHeroStatsCatalog(
    val heroStats: List<BattlegroundHeroStatsEntry> = emptyList(),
    val lastUpdateDate: String? = null,
    val dataPoints: Int? = null
)

@Serializable
data class BattlegroundHeroStatsEntry(
    val heroCardId: String,
    val dataPoints: Int = 0,
    val totalOffered: Int = 0,
    val totalPicked: Int = 0,
    val averagePosition: Double? = null,
    val conservativePositionEstimate: Double? = null,
    val tribeStats: List<BattlegroundHeroTribeStats> = emptyList()
)

@Serializable
data class BattlegroundHeroTribeStats(
    val tribe: Int,
    val averagePosition: Double? = null,
    val impactAveragePosition: Double? = null,
    val impactAveragePositionVsMissingTribe: Double? = null
)

@Serializable
data class BattlegroundHeroNameIndex(
    val version: String = "",
    val heroes: List<BattlegroundHeroNameEntry> = emptyList()
)

@Serializable
data class BattlegroundHeroNameEntry(
    val heroCardId: String,
    val name: String,
    @SerialName("localizedName")
    val localizedName: String = "",
    val aliases: List<String> = emptyList()
)

enum class HeroStatsMatchSource {
    NONE,
    HERO_CARD_ID,
    HERO_NAME_ALIAS
}

enum class HeroRecommendationTier {
    TOP_PICK,
    GOOD_PICK,
    NICHE,
    AVOID
}

data class HeroStrategyRecommendation(
    val tier: HeroRecommendationTier = HeroRecommendationTier.NICHE,
    val score: Int = 0,
    val recommendedCompId: String? = null,
    val recommendedCompName: String? = null,
    val fallbackCompId: String? = null,
    val fallbackCompName: String? = null,
    val pivotHint: String? = null,
    val summary: String = "",
    val reason: String = ""
)

data class ResolvedHeroStatOption(
    val slot: Int,
    val recognizedName: String? = null,
    val heroCardId: String? = null,
    val displayName: String,
    val localizedName: String? = null,
    val armor: Int? = null,
    val matchSource: HeroStatsMatchSource = HeroStatsMatchSource.NONE,
    val averagePosition: Double? = null,
    val conservativePositionEstimate: Double? = null,
    val dataPoints: Int? = null,
    val totalOffered: Int? = null,
    val totalPicked: Int? = null,
    val synergyTribes: List<Tribe> = emptyList(),
    val bestLobbyTribe: Tribe? = null,
    val bestLobbyImpact: Double? = null,
    val worstLobbyTribe: Tribe? = null,
    val worstLobbyImpact: Double? = null,
    val recommendation: HeroStrategyRecommendation? = null
) {
    val hasStats: Boolean
        get() = averagePosition != null

    val synergyCount: Int
        get() = synergyTribes.size
}
