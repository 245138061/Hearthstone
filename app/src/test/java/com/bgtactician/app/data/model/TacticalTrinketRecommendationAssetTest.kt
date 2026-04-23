package com.bgtactician.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TacticalTrinketRecommendationAssetTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `tactical trinket recommendation ids are valid`() {
        val strategyCatalog = decode<StrategyCatalog>("app/src/main/assets/strategies_zerotoheroes_zhCN.json")
        val metadataCatalog = decode<BattlegroundCardMetadataCatalog>("app/src/main/assets/bgs_card_metadata.json")
        val recommendationCatalog = decode<TacticalTrinketRecommendationCatalogTestModel>(
            "app/src/main/assets/tactical_trinket_recommendations.json"
        )

        val strategyIds = strategyCatalog.comps.map { it.id }.toSet()

        recommendationCatalog.recommendations.forEach { (strategyId, recommendation) ->
            assertTrue("未知流派: $strategyId", strategyId in strategyIds)

            recommendation.lesserCardIds.forEach { cardId ->
                val metadata = metadataCatalog.cards[cardId]
                assertTrue("缺少饰品元数据: $cardId", metadata != null)
                assertEquals("BATTLEGROUND_TRINKET", metadata?.type)
                assertEquals("LESSER_TRINKET", metadata?.spellSchool)
            }

            recommendation.greaterCardIds.forEach { cardId ->
                val metadata = metadataCatalog.cards[cardId]
                assertTrue("缺少饰品元数据: $cardId", metadata != null)
                assertEquals("BATTLEGROUND_TRINKET", metadata?.type)
                assertEquals("GREATER_TRINKET", metadata?.spellSchool)
            }
        }
    }

    private inline fun <reified T> decode(path: String): T {
        val payload = resolveAsset(path).readText()
        return json.decodeFromString(payload)
    }

    private fun resolveAsset(path: String): File {
        val candidates = mutableListOf(
            File(path),
            File("app/$path"),
            File("../$path"),
            File("../../$path")
        )
        val normalized = path.removePrefix("app/")
        candidates += listOf(
            File(normalized),
            File("app/$normalized"),
            File("../$normalized"),
            File("../../$normalized")
        )
        candidates.firstOrNull { it.exists() }?.let { return it }
        throw java.io.FileNotFoundException(
            buildString {
                append(path)
                append(" | checked: ")
                append(candidates.joinToString { it.path })
            }
        )
    }
}

@Serializable
private data class TacticalTrinketRecommendationCatalogTestModel(
    val recommendations: Map<String, TacticalTrinketRecommendationTestModel> = emptyMap()
)

@Serializable
private data class TacticalTrinketRecommendationTestModel(
    val lesserCardIds: List<String> = emptyList(),
    val greaterCardIds: List<String> = emptyList()
)
