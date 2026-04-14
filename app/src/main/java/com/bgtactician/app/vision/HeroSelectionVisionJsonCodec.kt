package com.bgtactician.app.vision

import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import com.bgtactician.app.data.model.VisionScreenType
import com.bgtactician.app.data.model.VisionTribe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object HeroSelectionVisionJsonCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun decode(rawJson: String): HeroSelectionVisionResult {
        val sanitized = sanitizeRawJson(rawJson)
        val root = json.parseToJsonElement(sanitized).jsonObject

        val screenType = normalizeScreenType(
            root.string("screen_type", "screen type", "screenType")
        )
        val availableTribes = root.array("available_tribes", "available tribes", "availableTribes")
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.let(::normalizeTribe) }
            ?.distinct()
            .orEmpty()
        val heroOptions = parseHeroOptions(
            root.array("hero_options", "hero options", "heroOptions")
        )

        return HeroSelectionVisionResult(
            screenType = screenType,
            availableTribes = availableTribes,
            heroOptions = heroOptions,
            confidence = root.float("confidence"),
            modelName = root.string("model_name", "model name", "modelName"),
            requestId = root.string("request_id", "request id", "requestId"),
            rawSummary = root.string("raw_summary", "raw summary", "rawSummary")
        )
    }

    private fun sanitizeRawJson(rawJson: String): String {
        return rawJson
            .replace('“', '"')
            .replace('”', '"')
            .replace('„', '"')
            .replace('‟', '"')
            .replace('‘', '\'')
            .replace('’', '\'')
    }

    private fun parseHeroOptions(array: JsonArray?): List<HeroSelectionVisionHeroOption> {
        return array?.mapIndexedNotNull { index, element ->
            when (element) {
                is JsonObject -> {
                    val selectable = element.boolean("selectable")
                    val locked = element.boolean("locked")
                    val isSelectable = selectable ?: locked?.not() ?: true
                    if (!isSelectable) return@mapIndexedNotNull null

                    HeroSelectionVisionHeroOption(
                        slot = element.int("slot") ?: index,
                        name = element.string("name"),
                        heroCardId = element.string("hero_card_id", "hero card id", "heroCardId"),
                        locked = false,
                        armor = element.int("armor"),
                        confidence = element.float("confidence")
                    )
                }

                is JsonPrimitive -> {
                    element.contentOrNull?.takeIf { it.isNotBlank() }?.let { heroName ->
                        HeroSelectionVisionHeroOption(
                            slot = index,
                            name = heroName,
                            locked = false
                        )
                    }
                }

                else -> null
            }
        }.orEmpty()
    }

    private fun normalizeScreenType(value: String?): VisionScreenType {
        return when (value.orEmpty().trim().lowercase()) {
            "hero_selection", "hero-selection", "hero selection", "selection", "select_hero" -> {
                VisionScreenType.HERO_SELECTION
            }

            "non_target", "non-target", "other", "not_target" -> VisionScreenType.NON_TARGET
            else -> VisionScreenType.UNKNOWN
        }
    }

    private fun normalizeTribe(value: String): VisionTribe? {
        val normalized = value.trim().lowercase()
        return when (normalized) {
            "beast", "野兽" -> VisionTribe.BEAST
            "demon", "恶魔" -> VisionTribe.DEMON
            "dragon", "龙" -> VisionTribe.DRAGON
            "elemental", "元素" -> VisionTribe.ELEMENTAL
            "mech", "机械" -> VisionTribe.MECH
            "murloc", "鱼人" -> VisionTribe.MURLOC
            "naga", "娜迦", "纳迦" -> VisionTribe.NAGA
            "pirate", "海盗" -> VisionTribe.PIRATE
            "quilboar", "quillboar", "野猪人" -> VisionTribe.QUILBOAR
            "undead", "亡灵" -> VisionTribe.UNDEAD
            else -> null
        }
    }

    private fun JsonObject.element(vararg keys: String): JsonElement? {
        return keys.firstNotNullOfOrNull { this[it] }
    }

    private fun JsonObject.string(vararg keys: String): String? {
        return element(*keys)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.int(vararg keys: String): Int? {
        return element(*keys)?.jsonPrimitive?.intOrNull
    }

    private fun JsonObject.float(vararg keys: String): Float? {
        return element(*keys)?.jsonPrimitive?.floatOrNull
    }

    private fun JsonObject.boolean(vararg keys: String): Boolean? {
        return element(*keys)?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.let {
            when (it) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
    }

    private fun JsonObject.array(vararg keys: String): JsonArray? {
        return element(*keys) as? JsonArray
    }
}
