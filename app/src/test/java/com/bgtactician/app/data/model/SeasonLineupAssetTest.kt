package com.bgtactician.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class SeasonLineupAssetTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `s13 lineup asset is decodable and grouped by primary tribe`() {
        val catalog = decode<SeasonLineupCatalog>("app/src/main/assets/s13_lineup_variants_zhCN.json")

        assertEquals("S13", catalog.season)
        assertEquals(listOf("野兽", "鱼人", "元素", "亡灵", "中立"), catalog.groups.map { it.name })
        assertEquals(20, catalog.groups.sumOf { it.variants.size })

        val variantIds = catalog.groups.flatMap { group -> group.variants.map { it.id } }
        assertEquals(variantIds.size, variantIds.toSet().size)

        val elemental = catalog.groups.first { it.id == "elemental" }
        assertTrue(elemental.variants.any { variant ->
            variant.id == "elemental_arcane_absorb" &&
                "巴琳达·斯通赫尔斯" in variant.finalBoard
        })
    }

    @Test
    fun `s13 lineup names resolve to battleground metadata`() {
        val catalog = decode<SeasonLineupCatalog>("app/src/main/assets/s13_lineup_variants_zhCN.json")
        val metadataCatalog = decode<BattlegroundCardMetadataCatalog>("app/src/main/assets/bgs_card_metadata.json")
        val minionCandidates = buildCandidates(
            metadataCatalog = metadataCatalog,
            type = "MINION"
        )
        val lesserTrinketCandidates = buildCandidates(
            metadataCatalog = metadataCatalog,
            type = "BATTLEGROUND_TRINKET",
            spellSchool = "LESSER_TRINKET"
        )
        val greaterTrinketCandidates = buildCandidates(
            metadataCatalog = metadataCatalog,
            type = "BATTLEGROUND_TRINKET",
            spellSchool = "GREATER_TRINKET"
        )

        catalog.groups.forEach { group ->
            group.variants.forEach { variant ->
                variant.finalBoard.forEach { name ->
                    assertTrue(
                        "未匹配到随从元数据: ${variant.id} -> $name",
                        resolveCandidateByName(name, minionCandidates) != null
                    )
                }

                variant.lesserTrinkets.forEach { name ->
                    assertTrue(
                        "未匹配到小饰品元数据: ${variant.id} -> $name",
                        resolveCandidateByName(name, lesserTrinketCandidates) != null
                    )
                }

                variant.greaterTrinkets.forEach { name ->
                    assertTrue(
                        "未匹配到大饰品元数据: ${variant.id} -> $name",
                        resolveCandidateByName(name, greaterTrinketCandidates) != null
                    )
                }
            }
        }
    }

    private inline fun <reified T> decode(path: String): T {
        val payload = resolveAsset(path).readText()
        return json.decodeFromString(payload)
    }

    private fun buildCandidates(
        metadataCatalog: BattlegroundCardMetadataCatalog,
        type: String,
        spellSchool: String? = null
    ): List<MetadataCardCandidate> {
        return metadataCatalog.cards.values.mapNotNull { metadata ->
            if (metadata.type != type) {
                null
            } else if (spellSchool != null && metadata.spellSchool != spellSchool) {
                null
            } else {
                MetadataCardCandidate(
                    names = listOfNotNull(
                        metadata.localizedName?.takeIf { it.isNotBlank() },
                        metadata.name.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun resolveCandidateByName(
        name: String,
        candidates: List<MetadataCardCandidate>
    ): MetadataCardCandidate? {
        val normalizedTarget = normalizeCardLookupName(name)
        if (normalizedTarget.isBlank()) return null

        candidates.firstOrNull { candidate ->
            candidate.names.any { candidateName ->
                normalizeCardLookupName(candidateName) == normalizedTarget
            }
        }?.let { return it }

        return candidates.mapNotNull { candidate ->
            candidate.names
                .map(::normalizeCardLookupName)
                .filter(String::isNotBlank)
                .mapNotNull { candidateName ->
                    when {
                        candidateName.contains(normalizedTarget) -> candidate to
                            (candidateName.length - normalizedTarget.length)

                        normalizedTarget.contains(candidateName) -> candidate to
                            (normalizedTarget.length - candidateName.length + 2_000)

                        else -> null
                    }
                }
                .minByOrNull { it.second }
        }.minByOrNull { it.second }?.first
    }

    private fun normalizeCardLookupName(value: String): String {
        return value.trim()
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
            .lowercase(Locale.ROOT)
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

    private data class MetadataCardCandidate(
        val names: List<String>
    )
}
