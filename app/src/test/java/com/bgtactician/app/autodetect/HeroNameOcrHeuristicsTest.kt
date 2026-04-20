package com.bgtactician.app.autodetect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroNameOcrHeuristicsTest {

    @Test
    fun `filters common ui noise text`() {
        val normalizedReroll = HeroNameOcrHeuristics.normalize("重掷")
        val normalizedBanner = HeroNameOcrHeuristics.normalize("选择一个英雄")
        val normalizedArmor = HeroNameOcrHeuristics.normalize("17")

        assertTrue(HeroNameOcrHeuristics.isLikelyNoise("重掷", normalizedReroll))
        assertTrue(HeroNameOcrHeuristics.isLikelyNoise("选择一个英雄", normalizedBanner))
        assertTrue(HeroNameOcrHeuristics.isLikelyNoise("17", normalizedArmor))
    }

    @Test
    fun `keeps likely hero names`() {
        val ysera = HeroNameOcrHeuristics.normalize("伊瑟拉")
        val omu = HeroNameOcrHeuristics.normalize("林地守护者欧穆")
        val skycaptain = HeroNameOcrHeuristics.normalize("钩牙船长")

        assertFalse(HeroNameOcrHeuristics.isLikelyNoise("伊瑟拉", ysera))
        assertFalse(HeroNameOcrHeuristics.isLikelyNoise("林地守护者欧穆", omu))
        assertFalse(HeroNameOcrHeuristics.isLikelyNoise("钩牙船长", skycaptain))
    }
}
