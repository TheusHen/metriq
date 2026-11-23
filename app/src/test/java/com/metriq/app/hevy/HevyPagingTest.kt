package com.metriq.app.hevy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HevyPagingTest {

    @Test
    fun `page limit covers the whole cache limit`() {
        assertTrue(HevyPaging.MAX_PAGES * HevyPaging.PAGE_SIZE >= HevyPaging.CACHE_LIMIT)
    }

    @Test
    fun `paging stops on a short page`() {
        assertTrue(HevyPaging.shouldStop(1, 12, 12, null))
    }

    @Test
    fun `paging stops once the cache limit is reached`() {
        assertTrue(HevyPaging.shouldStop(1, 25, HevyPaging.CACHE_LIMIT, null))
    }

    @Test
    fun `paging honours the declared page count`() {
        assertFalse(HevyPaging.shouldStop(2, 25, 50, 4))
        assertTrue(HevyPaging.shouldStop(4, 25, 100, 4))
    }

    @Test
    fun `paging continues on a full page below both limits`() {
        assertFalse(HevyPaging.shouldStop(1, 25, 25, null))
    }
}
