package com.bgtactician.app.data.repository

import com.bgtactician.app.data.model.BattlegroundCardMetadataCatalog
import com.bgtactician.app.data.model.BattlegroundCardMetadataEntry
import com.bgtactician.app.data.model.BattlegroundHeroNameEntry
import com.bgtactician.app.data.model.BattlegroundHeroNameIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyRepositoryHeroNameIndexMergeTest {

    private val repository = StrategyRepository()

    @Test
    fun `merge hero name index appends heroes from card metadata`() {
        val merged = repository.mergeHeroNameIndexWithCardMetadata(
            baseIndex = BattlegroundHeroNameIndex(
                version = "test",
                heroes = listOf(
                    BattlegroundHeroNameEntry(
                        heroCardId = "TB_BaconShop_HERO_53",
                        name = "Ysera",
                        localizedName = "伊瑟拉",
                        aliases = listOf("Ysera", "伊瑟拉")
                    )
                )
            ),
            cardMetadata = BattlegroundCardMetadataCatalog(
                cards = mapOf(
                    "BG35_HERO_001" to BattlegroundCardMetadataEntry(
                        name = "Genn, Worgen King",
                        localizedName = "吉恩，狼人国王",
                        type = "HERO"
                    )
                )
            )
        )

        val genn = merged.heroes.firstOrNull { it.heroCardId == "BG35_HERO_001" }
        assertEquals("Genn, Worgen King", genn?.name)
        assertEquals("吉恩，狼人国王", genn?.localizedName)
        assertTrue(genn?.aliases?.contains("Genn, Worgen King") == true)
        assertTrue(genn?.aliases?.contains("吉恩，狼人国王") == true)
    }

    @Test
    fun `merge hero name index preserves existing aliases while enriching metadata`() {
        val merged = repository.mergeHeroNameIndexWithCardMetadata(
            baseIndex = BattlegroundHeroNameIndex(
                version = "test",
                heroes = listOf(
                    BattlegroundHeroNameEntry(
                        heroCardId = "BG34_HERO_002",
                        name = "",
                        localizedName = "",
                        aliases = listOf("钟表先生")
                    )
                )
            ),
            cardMetadata = BattlegroundCardMetadataCatalog(
                cards = mapOf(
                    "BG34_HERO_002" to BattlegroundCardMetadataEntry(
                        name = "Mister Clocksworth",
                        localizedName = "钟表先生克劳沃斯",
                        type = "HERO"
                    )
                )
            )
        )

        val clocksworth = merged.heroes.first { it.heroCardId == "BG34_HERO_002" }
        assertEquals("Mister Clocksworth", clocksworth.name)
        assertEquals("钟表先生克劳沃斯", clocksworth.localizedName)
        assertTrue(clocksworth.aliases.contains("钟表先生"))
        assertTrue(clocksworth.aliases.contains("Mister Clocksworth"))
        assertTrue(clocksworth.aliases.contains("钟表先生克劳沃斯"))
    }
}
