package com.bgtactician.app.autodetect

import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroSelectionOcrStabilizerTest {

    @Test
    fun `locks slot after majority hits across five frames`() {
        var state = HeroSelectionOcrState()
        val frames = listOf(
            frame(slot0 = hero(slot = 0, id = "HERO_A", score = 0.92f)),
            frame(slot0 = hero(slot = 0, id = "HERO_A", score = 0.88f)),
            frame(slot0 = hero(slot = 0, id = null, score = 0.18f)),
            frame(slot0 = hero(slot = 0, id = "HERO_A", score = 0.91f)),
            frame(slot0 = hero(slot = 0, id = "HERO_A", score = 0.86f))
        )

        var step = HeroSelectionOcrStabilizer.advance(state, emptyList())
        frames.forEach { observations ->
            step = HeroSelectionOcrStabilizer.advance(step.state, observations)
        }

        assertEquals(1, step.stableLocks.size)
        assertEquals("HERO_A", step.stableLocks.single().heroCardId)
        assertEquals(0, step.stableLocks.single().slot)
        assertTrue(step.duplicateHeroCardIds.isEmpty())
    }

    @Test
    fun `marks duplicate stable heroes as conflict`() {
        var step = HeroSelectionOcrStabilizer.advance(HeroSelectionOcrState(), emptyList())
        repeat(3) {
            step = HeroSelectionOcrStabilizer.advance(
                step.state,
                listOf(
                    hero(slot = 0, id = "HERO_DUP", score = 0.91f),
                    hero(slot = 1, id = "HERO_DUP", score = 0.89f)
                )
            )
        }

        assertEquals(setOf("HERO_DUP"), step.duplicateHeroCardIds)
        assertTrue(step.stableOptions.isEmpty())
    }

    @Test
    fun `keeps stable lock across temporary misses`() {
        var step = HeroSelectionOcrStabilizer.advance(HeroSelectionOcrState(), emptyList())
        repeat(3) {
            step = HeroSelectionOcrStabilizer.advance(
                step.state,
                listOf(hero(slot = 1, id = "HERO_A", score = 0.9f))
            )
        }

        repeat(2) {
            step = HeroSelectionOcrStabilizer.advance(
                step.state,
                listOf(hero(slot = 1, id = null, score = 0.12f))
            )
        }

        assertEquals(1, step.stableLocks.size)
        assertEquals("HERO_A", step.stableLocks.single().heroCardId)
        assertEquals(1, step.stableLocks.single().slot)
    }

    @Test
    fun `switches lock only after sustained stronger evidence`() {
        var step = HeroSelectionOcrStabilizer.advance(HeroSelectionOcrState(), emptyList())
        repeat(3) {
            step = HeroSelectionOcrStabilizer.advance(
                step.state,
                listOf(hero(slot = 0, id = "HERO_A", score = 0.9f))
            )
        }

        repeat(2) {
            step = HeroSelectionOcrStabilizer.advance(
                step.state,
                listOf(hero(slot = 0, id = "HERO_B", score = 0.91f))
            )
        }

        assertEquals("HERO_A", step.stableLocks.single().heroCardId)

        step = HeroSelectionOcrStabilizer.advance(
            step.state,
            listOf(hero(slot = 0, id = "HERO_B", score = 0.93f))
        )
        step = HeroSelectionOcrStabilizer.advance(
            step.state,
            listOf(hero(slot = 0, id = "HERO_B", score = 0.94f))
        )

        assertEquals("HERO_B", step.stableLocks.single().heroCardId)
    }

    @Test
    fun `marks slot chaotic after three different hero ids`() {
        var step = HeroSelectionOcrStabilizer.advance(HeroSelectionOcrState(), emptyList())
        val ids = listOf("HERO_A", "HERO_B", "HERO_C", "HERO_A", "HERO_B")
        ids.forEach { heroId ->
            step = HeroSelectionOcrStabilizer.advance(
                step.state,
                listOf(hero(slot = 2, id = heroId, score = 0.82f))
            )
        }

        assertTrue(step.chaoticSlots.contains(2))
        assertFalse(step.duplicateHeroCardIds.contains("HERO_A"))
    }

    private fun frame(
        slot0: HeroSelectionOcrObservation? = null,
        slot1: HeroSelectionOcrObservation? = null,
        slot2: HeroSelectionOcrObservation? = null,
        slot3: HeroSelectionOcrObservation? = null
    ): List<HeroSelectionOcrObservation> {
        return listOfNotNull(slot0, slot1, slot2, slot3)
    }

    private fun hero(
        slot: Int,
        id: String?,
        score: Float
    ): HeroSelectionOcrObservation {
        return HeroSelectionOcrObservation(
            slot = slot,
            heroCardId = id,
            option = id?.let {
                HeroSelectionVisionHeroOption(
                    slot = slot,
                    name = "Hero-$it",
                    heroCardId = it,
                    preferNameMatchSource = true,
                    confidence = score
                )
            },
            score = score
        )
    }
}
