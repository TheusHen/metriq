package com.metriq.app.hevy

/** Paging policy kept separate from the API/repository so it can be verified on the JVM. */
internal object HevyPaging {
    const val MAX_PAGES = 5
    const val PAGE_SIZE = 25
    const val CACHE_LIMIT = 120

    /** True once a short page, the cache limit or the API's declared last page is reached. */
    fun shouldStop(
        page: Int,
        fetchedCount: Int,
        cachedCount: Int,
        declaredPageCount: Int?,
    ): Boolean {
        if (fetchedCount < PAGE_SIZE) return true
        if (cachedCount >= CACHE_LIMIT) return true
        return declaredPageCount?.takeIf { it > 0 }?.let { page >= it } ?: false
    }
}
