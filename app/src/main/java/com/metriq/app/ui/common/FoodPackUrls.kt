package com.metriq.app.ui.common

import java.time.YearMonth

/**
 * Food packs are published under a `packs-YYYY-MM` GitHub release tag. The tag follows
 * the current month so a shipped build keeps finding fresh packs without an app update.
 */
internal object FoodPackUrls {
    const val RELEASE_PREFIX = "https://github.com/TheusHen/metriq/releases/download/packs-"

    fun currentTagBase(yearMonth: YearMonth = YearMonth.now()): String = RELEASE_PREFIX + yearMonth.toString()

    /** A blank override falls back to the current-month tag; a manual URL is trimmed. */
    fun resolveBaseUrl(override: String?, yearMonth: YearMonth = YearMonth.now()): String =
        override?.trim()?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: currentTagBase(yearMonth)
}
