package com.bgtactician.app.autodetect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernTierMonitorStabilizerTest {

    @Test
    fun `promotes tavern tier after two matching detections`() {
        val first = TavernTierDetectorStep(tier = 4, confidence = 0.72f)
        val second = TavernTierDetectorStep(tier = 4, confidence = 0.76f)

        val step1 = TavernTierMonitorStabilizer.advance(TavernTierMonitorState(), first.toDetection())
        val step2 = TavernTierMonitorStabilizer.advance(step1.state, second.toDetection())

        assertFalse(step1.changed)
        assertTrue(step2.changed)
        assertEquals(4, step2.updatedStableDetection?.tier)
    }

    @Test
    fun `requires three misses before clearing stable tavern tier`() {
        val detected = TavernTierDetectorStep(tier = 3, confidence = 0.68f)
        val miss = TavernTierDetectorStep(tier = null, confidence = 0f)

        val locked = TavernTierMonitorStabilizer.advance(
            TavernTierMonitorStabilizer.advance(TavernTierMonitorState(), detected.toDetection()).state,
            detected.toDetection()
        )

        val miss1 = TavernTierMonitorStabilizer.advance(locked.state, miss.toDetection())
        val miss2 = TavernTierMonitorStabilizer.advance(miss1.state, miss.toDetection())
        val miss3 = TavernTierMonitorStabilizer.advance(miss2.state, miss.toDetection())

        assertFalse(miss1.changed)
        assertFalse(miss2.changed)
        assertTrue(miss3.changed)
        assertNull(miss3.updatedStableDetection?.tier)
    }

    @Test
    fun `switches to new tavern tier after two matching updates`() {
        val tierTwo = TavernTierDetectorStep(tier = 2, confidence = 0.66f)
        val tierFive = TavernTierDetectorStep(tier = 5, confidence = 0.83f)

        val lockedTierTwo = TavernTierMonitorStabilizer.advance(
            TavernTierMonitorStabilizer.advance(TavernTierMonitorState(), tierTwo.toDetection()).state,
            tierTwo.toDetection()
        )

        val firstTierFive = TavernTierMonitorStabilizer.advance(lockedTierTwo.state, tierFive.toDetection())
        val secondTierFive = TavernTierMonitorStabilizer.advance(firstTierFive.state, tierFive.toDetection())

        assertFalse(firstTierFive.changed)
        assertTrue(secondTierFive.changed)
        assertEquals(5, secondTierFive.updatedStableDetection?.tier)
    }

    private data class TavernTierDetectorStep(
        val tier: Int?,
        val confidence: Float
    ) {
        fun toDetection(): TavernTierDetection {
            return TavernTierDetection(
                tier = tier,
                confidence = confidence,
                sourceLabel = if (tier != null) "test" else null,
                debugLabel = if (tier != null) null else "miss"
            )
        }
    }
}
