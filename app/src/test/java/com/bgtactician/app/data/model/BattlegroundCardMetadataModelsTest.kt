package com.bgtactician.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlegroundCardMetadataModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `can decode battleground card metadata payload`() {
        val payload = """
            {
              "version": "test",
              "generated_at": "2026-04-16T00:00:00Z",
              "race_tags": {
                "MURLOC": 2536
              },
              "summary": {
                "card_count": 2,
                "type_counts": {
                  "MINION": 1,
                  "BATTLEGROUND_SPELL": 1
                }
              },
              "cards": {
                "BG21_005": {
                  "dbf_id": 72067,
                  "name": "Famished Felbat",
                  "localized_name": "饥饿的魔蝠",
                  "type": "MINION",
                  "tech_level": 6,
                  "races": ["DEMON"],
                  "is_pool_minion": true,
                  "premium_card_id": "BG21_005_G"
                },
                "EBG_Spell_017": {
                  "dbf_id": 100601,
                  "name": "Eyes of the Earth Mother",
                  "localized_name": "大地母亲之眼",
                  "type": "BATTLEGROUND_SPELL",
                  "tech_level": 6,
                  "spell_school": "TAVERN",
                  "is_pool_spell": true
                }
              }
            }
        """.trimIndent()

        val catalog = json.decodeFromString<BattlegroundCardMetadataCatalog>(payload)

        assertEquals("test", catalog.version)
        assertEquals(2536, catalog.raceTags["MURLOC"])
        assertEquals(2, catalog.summary?.cardCount)

        val felbat = catalog.cards.getValue("BG21_005")
        assertEquals("MINION", felbat.type)
        assertEquals(6, felbat.techLevel)
        assertEquals(listOf("DEMON"), felbat.races)
        assertTrue(felbat.isPoolMinion)
        assertFalse(felbat.isPoolSpell)

        val tavernSpell = catalog.cards.getValue("EBG_Spell_017")
        assertEquals("BATTLEGROUND_SPELL", tavernSpell.type)
        assertEquals("TAVERN", tavernSpell.spellSchool)
        assertTrue(tavernSpell.isPoolSpell)
    }
}
