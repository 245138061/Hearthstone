package com.bgtactician.app.data.local

import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.HeroStatsMatchSource
import com.bgtactician.app.data.model.ResolvedHeroStatOption
import com.bgtactician.app.data.model.Tribe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroSelectionSessionStoreTest {

    @After
    fun tearDown() {
        HeroSelectionSessionStore.clearRecognizedHeroes(selectedTribes = emptySet())
    }

    @Test
    fun `update vision result keeps selected hero and latest hero options`() {
        val tribes = stableTribes()
        val firstOptions = listOf(
            HeroSelectionVisionHeroOption(slot = 0, name = "伊瑟拉"),
            HeroSelectionVisionHeroOption(slot = 1, name = "瓦托格尔女王")
        )
        val firstHeroes = listOf(
            resolvedHero(slot = 0, heroCardId = "TB_BaconShop_HERO_12", displayName = "伊瑟拉"),
            resolvedHero(slot = 1, heroCardId = "TB_BaconShop_HERO_11", displayName = "瓦托格尔女王")
        )
        HeroSelectionSessionStore.updateVisionResult(
            selectedTribes = tribes,
            recognizedHeroOptions = firstOptions,
            recognizedHeroes = firstHeroes
        )
        HeroSelectionSessionStore.selectHero(firstHeroes[1])

        val refreshedOptions = listOf(
            HeroSelectionVisionHeroOption(slot = 0, name = "Ysera", heroCardId = "TB_BaconShop_HERO_12"),
            HeroSelectionVisionHeroOption(slot = 1, name = "Queen Wagtoggle", heroCardId = "TB_BaconShop_HERO_11")
        )
        val refreshedHeroes = listOf(
            resolvedHero(slot = 0, heroCardId = "TB_BaconShop_HERO_12", displayName = "伊瑟拉"),
            resolvedHero(slot = 1, heroCardId = "TB_BaconShop_HERO_11", displayName = "瓦托格尔女王")
        )

        HeroSelectionSessionStore.updateVisionResult(
            selectedTribes = tribes,
            recognizedHeroOptions = refreshedOptions,
            recognizedHeroes = refreshedHeroes
        )

        val state = HeroSelectionSessionStore.state.value
        assertEquals(tribes, state.selectedTribes)
        assertEquals(refreshedOptions, state.recognizedHeroOptions)
        assertEquals("TB_BaconShop_HERO_11", state.selectedHeroCardId)
        assertEquals("瓦托格尔女王", state.selectedHeroName)
        assertEquals(1, state.selectedHeroSlot)
    }

    @Test
    fun `update manual tribes keeps hero options and selected hero when match remains`() {
        val initialTribes = stableTribes()
        val correctedTribes = setOf(
            Tribe.BEAST,
            Tribe.DEMON,
            Tribe.DRAGON,
            Tribe.MECH,
            Tribe.NAGA
        )
        val heroOptions = listOf(
            HeroSelectionVisionHeroOption(slot = 0, name = "伊瑟拉", heroCardId = "TB_BaconShop_HERO_12")
        )
        val initialHero = resolvedHero(
            slot = 0,
            heroCardId = "TB_BaconShop_HERO_12",
            displayName = "伊瑟拉"
        )
        HeroSelectionSessionStore.updateVisionResult(
            selectedTribes = initialTribes,
            recognizedHeroOptions = heroOptions,
            recognizedHeroes = listOf(initialHero)
        )
        HeroSelectionSessionStore.selectHero(initialHero)

        HeroSelectionSessionStore.updateManualTribes(
            selectedTribes = correctedTribes,
            recognizedHeroes = listOf(
                resolvedHero(
                    slot = 0,
                    heroCardId = "TB_BaconShop_HERO_12",
                    displayName = "伊瑟拉"
                )
            )
        )

        val state = HeroSelectionSessionStore.state.value
        assertEquals(correctedTribes, state.selectedTribes)
        assertEquals(heroOptions, state.recognizedHeroOptions)
        assertEquals("TB_BaconShop_HERO_12", state.selectedHeroCardId)
        assertEquals(0, state.selectedHeroSlot)
    }

    @Test
    fun `clear recognized heroes clears hero state and can keep tribes`() {
        val tribes = stableTribes()
        HeroSelectionSessionStore.updateVisionResult(
            selectedTribes = tribes,
            recognizedHeroOptions = listOf(
                HeroSelectionVisionHeroOption(slot = 0, name = "伊瑟拉")
            ),
            recognizedHeroes = listOf(
                resolvedHero(slot = 0, heroCardId = "TB_BaconShop_HERO_12", displayName = "伊瑟拉")
            )
        )

        HeroSelectionSessionStore.clearRecognizedHeroes(selectedTribes = tribes)

        val state = HeroSelectionSessionStore.state.value
        assertEquals(tribes, state.selectedTribes)
        assertTrue(state.recognizedHeroOptions.isEmpty())
        assertTrue(state.recognizedHeroes.isEmpty())
        assertEquals(null, state.selectedHeroCardId)
        assertEquals(null, state.selectedHeroName)
        assertEquals(null, state.selectedHeroSlot)
    }

    private fun resolvedHero(
        slot: Int,
        heroCardId: String,
        displayName: String
    ) = ResolvedHeroStatOption(
        slot = slot,
        recognizedName = displayName,
        heroCardId = heroCardId,
        displayName = displayName,
        matchSource = HeroStatsMatchSource.HERO_CARD_ID
    )

    private fun stableTribes() = setOf(
        Tribe.BEAST,
        Tribe.DEMON,
        Tribe.MECH,
        Tribe.NAGA,
        Tribe.UNDEAD
    )
}
