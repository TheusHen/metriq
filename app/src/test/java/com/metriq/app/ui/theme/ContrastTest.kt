package com.metriq.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the readability of the dark palette. WCAG 2.1 AA for normal text is 4.5:1;
 * these values are the floor for every text token, so a future palette tweak cannot
 * silently drop captions below it.
 */
class ContrastTest {

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val surfaces = listOf(
        "bg" to MetriqColors.bg,
        "surface1" to MetriqColors.surface1,
        "surface2" to MetriqColors.surface2,
    )

    @Test
    fun bodyAndSecondaryTextMeetAa() {
        listOf(
            "ink" to MetriqColors.ink,
            "ink2" to MetriqColors.ink2,
        ).forEach { (name, ink) ->
            surfaces.forEach { (surfaceName, surface) ->
                val contrast = ratio(ink, surface)
                assertTrue(contrast >= 4.5, "$name on $surfaceName is ${"%.2f".format(contrast)}:1")
            }
        }
    }

    @Test
    fun tertiaryTextMeetsAa() {
        surfaces.forEach { (surfaceName, surface) ->
            val contrast = ratio(MetriqColors.ink3, surface)
            assertTrue(contrast >= 4.5, "ink3 on $surfaceName is ${"%.2f".format(contrast)}:1")
        }
    }

    @Test
    fun accentButtonLabelMeetsAa() {
        val contrast = ratio(MetriqColors.accentInk, MetriqColors.accent)
        assertTrue(contrast >= 4.5, "accentInk on accent is ${"%.2f".format(contrast)}:1")
    }

    @Test
    fun decorativeInkIsNotUsedForText() {
        // Documents the intent: ink4 is below AA on purpose and must stay non-textual.
        val contrast = ratio(MetriqColors.ink4, MetriqColors.bg)
        assertTrue(contrast < 4.5, "ink4 should stay decorative, but reached ${"%.2f".format(contrast)}:1")
    }
}
