package com.bgtactician.app.data.repository

import android.content.Context
import com.bgtactician.app.data.model.Tribe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object MinionTribeIndex {

    private const val ASSET_FILE = "bgs_card_metadata.json"

    @Volatile
    private var cached: Map<String, Set<Tribe>>? = null

    fun get(context: Context): Map<String, Set<Tribe>> {
        cached?.let { return it }
        return synchronized(this) {
            cached?.let { return@synchronized it }
            val parsed = runCatching {
                val root = Json.parseToJsonElement(
                    context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
                ).jsonObject
                root["cards"]
                    ?.jsonObject
                    ?.mapValues { (_, value) ->
                        value.jsonObject["races"]
                            ?.jsonArray
                            ?.mapNotNull { raceElement ->
                                raceElement.jsonPrimitive.contentOrNull?.let(Tribe::fromMetadataRace)
                            }
                            ?.toSet()
                            .orEmpty()
                    }
                    .orEmpty()
            }.getOrDefault(emptyMap())
            cached = parsed
            parsed
        }
    }
}
