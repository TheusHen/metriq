package com.metriq.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** The current-month release tag is computed, never hardcoded to a past month. */
class FoodPackUrlsTest {

    @Test
    fun `current tag uses the supplied year month`() {
        assertEquals(
            "https://github.com/TheusHen/metriq/releases/download/packs-2026-11",
            FoodPackUrls.currentTagBase(YearMonth.of(2026, 11)),
        )
    }

    @Test
    fun `blank override falls back to the current tag`() {
        assertEquals(
            FoodPackUrls.currentTagBase(YearMonth.of(2026, 12)),
            FoodPackUrls.resolveBaseUrl("  ", YearMonth.of(2026, 12)),
        )
    }

    @Test
    fun `manual override is trimmed and keeps a trailing slash harmless`() {
        assertEquals(
            "https://example.org/packs",
            FoodPackUrls.resolveBaseUrl("  https://example.org/packs/  ", YearMonth.of(2026, 11)),
        )
    }
}
