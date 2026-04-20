package com.bgtactician.app.vision

import com.bgtactician.app.data.model.BattlegroundHeroNameEntry
import com.bgtactician.app.data.model.BattlegroundHeroNameIndex
import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import com.bgtactician.app.data.model.VisionScreenType
import com.bgtactician.app.data.model.VisionTribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroSelectionVisionValidatorTest {

    @Test
    fun allowsHeroResultsWhenTribesAreIncompleteInRelaxedMode() {
        val result = HeroSelectionVisionResult(
            screenType = VisionScreenType.NON_TARGET,
            availableTribes = listOf(VisionTribe.DEMON, VisionTribe.BEAST),
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(slot = 0, name = "伊瑟拉", locked = false),
                HeroSelectionVisionHeroOption(slot = 1, name = "恐龙大师布莱恩", locked = false)
            )
        )

        assertTrue(
            HeroSelectionVisionValidator.validate(
                result,
                requireCompleteTribes = false
            ).isValid
        )
        assertFalse(HeroSelectionVisionValidator.validate(result).isValid)
    }

    @Test
    fun `semantic validator rejects hero results when no option matches local index`() {
        val result = HeroSelectionVisionResult(
            screenType = VisionScreenType.HERO_SELECTION,
            availableTribes = listOf(
                VisionTribe.DEMON,
                VisionTribe.BEAST,
                VisionTribe.MECH,
                VisionTribe.DRAGON,
                VisionTribe.NAGA
            ),
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(slot = 0, name = "完全未知英雄A"),
                HeroSelectionVisionHeroOption(slot = 1, name = "完全未知英雄B")
            )
        )

        val validation = HeroSelectionVisionSemanticValidator.validate(
            result = result,
            heroNameIndex = heroNameIndex()
        )

        assertFalse(validation.isReliable)
        assertEquals(listOf("英雄未命中本地索引"), validation.errors)
    }

    @Test
    fun `detailed recovery validator keeps ai hero results when only semantic warnings exist`() {
        val result = HeroSelectionVisionResult(
            screenType = VisionScreenType.HERO_SELECTION,
            availableTribes = listOf(
                VisionTribe.DEMON,
                VisionTribe.BEAST,
                VisionTribe.MECH,
                VisionTribe.DRAGON,
                VisionTribe.NAGA
            ),
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(slot = 0, name = "完全未知英雄A"),
                HeroSelectionVisionHeroOption(slot = 1, name = "完全未知英雄B")
            )
        )

        val validation = HeroSelectionVisionDetailedRecoveryValidator.validate(
            result = result,
            heroNameIndex = heroNameIndex()
        )

        assertTrue(validation.isRecoverable)
        assertTrue(validation.structuralErrors.isEmpty())
        assertEquals(listOf("英雄未命中本地索引"), validation.semanticWarnings)
    }

    @Test
    fun `detailed recovery validator still blocks structurally invalid hero results`() {
        val result = HeroSelectionVisionResult(
            screenType = VisionScreenType.HERO_SELECTION,
            availableTribes = listOf(
                VisionTribe.DEMON,
                VisionTribe.BEAST,
                VisionTribe.MECH,
                VisionTribe.DRAGON,
                VisionTribe.NAGA
            ),
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(slot = 0, name = "伊瑟拉"),
                HeroSelectionVisionHeroOption(slot = 0, name = "瓦托格尔女王")
            )
        )

        val validation = HeroSelectionVisionDetailedRecoveryValidator.validate(
            result = result,
            heroNameIndex = heroNameIndex()
        )

        assertFalse(validation.isRecoverable)
        assertEquals(listOf("hero_options.slot 存在重复"), validation.structuralErrors)
    }

    @Test
    fun `semantic validator rejects duplicate hero matches across multiple slots`() {
        val result = HeroSelectionVisionResult(
            screenType = VisionScreenType.HERO_SELECTION,
            availableTribes = listOf(
                VisionTribe.DEMON,
                VisionTribe.BEAST,
                VisionTribe.MECH,
                VisionTribe.DRAGON,
                VisionTribe.NAGA
            ),
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(slot = 0, name = "瓦托格尔女王"),
                HeroSelectionVisionHeroOption(slot = 1, name = "Queen Wagtoggle")
            )
        )

        val validation = HeroSelectionVisionSemanticValidator.validate(
            result = result,
            heroNameIndex = heroNameIndex()
        )

        assertFalse(validation.isReliable)
        assertEquals(listOf("多个候选英雄命中同一英雄"), validation.errors)
    }

    @Test
    fun `semantic validator rejects duplicate raw hero names across slots`() {
        val result = HeroSelectionVisionResult(
            screenType = VisionScreenType.HERO_SELECTION,
            availableTribes = listOf(
                VisionTribe.DEMON,
                VisionTribe.BEAST,
                VisionTribe.MECH,
                VisionTribe.DRAGON,
                VisionTribe.NAGA
            ),
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(slot = 0, name = "完全未知英雄"),
                HeroSelectionVisionHeroOption(slot = 1, name = "完全未知英雄")
            )
        )

        val validation = HeroSelectionVisionSemanticValidator.validate(
            result = result,
            heroNameIndex = heroNameIndex()
        )

        assertFalse(validation.isReliable)
        assertEquals(
            listOf("英雄未命中本地索引", "多个候选英雄名称重复"),
            validation.errors
        )
    }

    @Test
    fun `semantic validator rejects conflicting hero id and name`() {
        val result = HeroSelectionVisionResult(
            screenType = VisionScreenType.HERO_SELECTION,
            availableTribes = listOf(
                VisionTribe.DEMON,
                VisionTribe.BEAST,
                VisionTribe.MECH,
                VisionTribe.DRAGON,
                VisionTribe.NAGA
            ),
            heroOptions = listOf(
                HeroSelectionVisionHeroOption(
                    slot = 0,
                    name = "伊瑟拉",
                    heroCardId = "TB_BaconShop_HERO_11"
                )
            )
        )

        val validation = HeroSelectionVisionSemanticValidator.validate(
            result = result,
            heroNameIndex = heroNameIndex()
        )

        assertFalse(validation.isReliable)
        assertEquals(listOf("hero_card_id 与英雄名称冲突"), validation.errors)
    }

    private fun heroNameIndex() = BattlegroundHeroNameIndex(
        version = "test",
        heroes = listOf(
            BattlegroundHeroNameEntry(
                heroCardId = "TB_BaconShop_HERO_11",
                name = "Queen Wagtoggle",
                localizedName = "瓦托格尔女王",
                aliases = listOf("瓦托格尔女王")
            ),
            BattlegroundHeroNameEntry(
                heroCardId = "TB_BaconShop_HERO_12",
                name = "Ysera",
                localizedName = "伊瑟拉",
                aliases = listOf("伊瑟拉")
            )
        )
    )
}
