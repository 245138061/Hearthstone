package com.bgtactician.app.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroSelectionVisionJsonCodecTest {

    @Test
    fun `decode filters non selectable hero options from mixed boolean formats`() {
        val raw = """
            {
              "screen_type": "hero_selection",
              "available_tribes": ["Beast", "Demon", "Dragon", "Mech", "Naga"],
              "hero_options": [
                {"slot": 0, "name": "可选A", "locked": false},
                {"slot": 1, "name": "不可选B", "locked": 1},
                {"slot": 2, "name": "不可选C", "is_locked": "true"},
                {"slot": 3, "name": "可选D", "selectable": "1"}
              ]
            }
        """.trimIndent()

        val decoded = HeroSelectionVisionJsonCodec.decode(raw)

        assertEquals(listOf(0, 3), decoded.selectableHeroOptions.map { it.slot })
        assertEquals(listOf("可选A", "可选D"), decoded.selectableHeroOptions.map { it.name })
    }

    @Test
    fun `decode filters non selectable hero options from disabled aliases`() {
        val raw = """
            {
              "screen_type": "hero_selection",
              "available_tribes": ["Beast", "Demon", "Dragon", "Mech", "Naga"],
              "hero_options": [
                {"slot": 0, "name": "可选A", "enabled": true},
                {"slot": 1, "name": "不可选B", "disabled": true},
                {"slot": 2, "name": "不可选C", "unavailable": "yes"},
                {"slot": 3, "name": "可选D", "can_pick": true}
              ]
            }
        """.trimIndent()

        val decoded = HeroSelectionVisionJsonCodec.decode(raw)

        assertEquals(listOf(0, 3), decoded.selectableHeroOptions.map { it.slot })
        assertEquals(listOf("可选A", "可选D"), decoded.selectableHeroOptions.map { it.name })
    }

    @Test
    fun `decode treats locked keyword as locked state`() {
        val raw = """
            {
              "screen_type": "hero_selection",
              "available_tribes": ["Beast", "Demon", "Dragon", "Mech", "Naga"],
              "hero_options": [
                {"slot": 0, "name": "可选A", "locked": "false"},
                {"slot": 1, "name": "不可选B", "locked": "locked"}
              ]
            }
        """.trimIndent()

        val decoded = HeroSelectionVisionJsonCodec.decode(raw)

        assertEquals(listOf(0), decoded.selectableHeroOptions.map { it.slot })
        assertEquals(listOf("可选A"), decoded.selectableHeroOptions.map { it.name })
    }
}
