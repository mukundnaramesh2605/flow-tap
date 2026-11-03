package com.flowtap.data.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessLevelTest {
    @Test
    fun `ends map to the system range without hitting zero`() {
        assertEquals(1, brightnessLevel(0))
        assertEquals(255, brightnessLevel(100))
    }

    @Test
    fun `out of range input is clamped`() {
        assertEquals(1, brightnessLevel(-20))
        assertEquals(255, brightnessLevel(150))
    }

    @Test
    fun `curve is increasing and perceptual`() {
        val levels = (0..100).map(::brightnessLevel)
        assertTrue(levels.zipWithNext().all { (a, b) -> b >= a })
        // Half on the slider is well under half the backlight, like the system slider.
        assertTrue(brightnessLevel(50) in 40..80)
    }
}
