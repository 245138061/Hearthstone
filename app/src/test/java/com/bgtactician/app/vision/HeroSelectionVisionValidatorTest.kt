package com.bgtactician.app.vision

import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import com.bgtactician.app.data.model.VisionScreenType
import com.bgtactician.app.data.model.VisionTribe
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
}
