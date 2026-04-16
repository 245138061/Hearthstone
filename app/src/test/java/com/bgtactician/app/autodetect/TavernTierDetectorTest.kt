package com.bgtactician.app.autodetect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernTierDetectorTest {

    @Test
    fun `countGoldStarsFromMask ignores extra highlight above five stars`() {
        val width = 42
        val height = 20
        val mask = BooleanArray(width * height)

        addBlock(mask, width, left = 2, top = 11, blockWidth = 3, blockHeight = 3)
        addBlock(mask, width, left = 9, top = 10, blockWidth = 3, blockHeight = 3)
        addBlock(mask, width, left = 16, top = 11, blockWidth = 3, blockHeight = 3)
        addBlock(mask, width, left = 23, top = 10, blockWidth = 3, blockHeight = 3)
        addBlock(mask, width, left = 30, top = 11, blockWidth = 3, blockHeight = 3)
        addBlock(mask, width, left = 27, top = 3, blockWidth = 2, blockHeight = 2)

        val stars = TavernTierDetector.countGoldStarsFromMask(width = width, height = height, mask = mask)

        assertEquals(5, stars)
    }

    @Test
    fun `countGoldStarsFromMask ignores extra highlight above three stars`() {
        val width = 34
        val height = 18
        val mask = BooleanArray(width * height)

        addBlock(mask, width, left = 4, top = 9, blockWidth = 3, blockHeight = 3)
        addBlock(mask, width, left = 13, top = 10, blockWidth = 3, blockHeight = 3)
        addBlock(mask, width, left = 22, top = 9, blockWidth = 3, blockHeight = 3)
        addBlock(mask, width, left = 8, top = 2, blockWidth = 2, blockHeight = 2)

        val stars = TavernTierDetector.countGoldStarsFromMask(width = width, height = height, mask = mask)

        assertEquals(3, stars)
    }

    @Test
    fun `isTemplateBadgeBoundsPlausible rejects narrow false positive in template only mode`() {
        val plausible = TavernTierDetector.isTemplateBadgeBoundsPlausible(
            viewportWidth = 2560,
            viewportHeight = 1182,
            boundsWidth = 27,
            boundsHeight = 70,
            templateOnly = true
        )

        assertFalse(plausible)
    }

    @Test
    fun `isTemplateBadgeBoundsPlausible accepts tavern badge bounds in template only mode`() {
        val plausible = TavernTierDetector.isTemplateBadgeBoundsPlausible(
            viewportWidth = 2560,
            viewportHeight = 1182,
            boundsWidth = 53,
            boundsHeight = 116,
            templateOnly = true
        )

        assertTrue(plausible)
    }

    @Test
    fun `resolveTemplateOnlyConsensus prefers direct result when anchored is missing`() {
        val resolved = TavernTierDetector.resolveTemplateOnlyConsensus(
            directTier = 6,
            anchoredTier = null
        )

        assertEquals(6, resolved)
    }

    @Test
    fun `resolveTemplateOnlyConsensus prefers anchored result when direct is missing`() {
        val resolved = TavernTierDetector.resolveTemplateOnlyConsensus(
            directTier = null,
            anchoredTier = 5
        )

        assertEquals(5, resolved)
    }

    @Test
    fun `resolveTemplateOnlyConsensus rejects disagreement`() {
        val resolved = TavernTierDetector.resolveTemplateOnlyConsensus(
            directTier = 1,
            anchoredTier = 4
        )

        assertEquals(null, resolved)
    }

    private fun addBlock(
        mask: BooleanArray,
        width: Int,
        left: Int,
        top: Int,
        blockWidth: Int,
        blockHeight: Int
    ) {
        for (y in top until top + blockHeight) {
            for (x in left until left + blockWidth) {
                mask[y * width + x] = true
            }
        }
    }
}
