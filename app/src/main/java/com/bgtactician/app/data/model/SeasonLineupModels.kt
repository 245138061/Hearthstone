package com.bgtactician.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeasonLineupCatalog(
    val season: String,
    val title: String,
    val groups: List<SeasonLineupGroup> = emptyList()
)

@Serializable
data class SeasonLineupGroup(
    val id: String,
    val name: String,
    val variants: List<SeasonLineupVariant> = emptyList()
)

@Serializable
data class SeasonLineupVariant(
    val id: String,
    val name: String,
    val tribes: List<String> = emptyList(),
    @SerialName("final_board")
    val finalBoard: List<String> = emptyList(),
    @SerialName("lesser_trinkets")
    val lesserTrinkets: List<String> = emptyList(),
    @SerialName("greater_trinkets")
    val greaterTrinkets: List<String> = emptyList(),
    val notes: String = ""
)
