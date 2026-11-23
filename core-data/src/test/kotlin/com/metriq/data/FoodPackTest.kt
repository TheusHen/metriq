package com.metriq.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodPackTest {

    private fun row(name: String, kcal: String = "120"): List<String> = MutableList(26) { "" }.apply {
        this[0] = name
        this[1] = "Pack Brand"
        this[2] = "snack"
        this[4] = kcal
        this[5] = "7"
        this[11] = "2.5" // saturatedFat -> foodMicros
    }

    private fun pack(header: Boolean = true): String {
        val headerRow = MutableList(26) { "h$it" }.apply { this[0] = "name"; this[4] = "kcal" }
        val lines = mutableListOf(row("Pack food A"), row("Pack food B", "90"))
        if (header) lines.add(0, headerRow)
        return lines.joinToString("\n") { it.joinToString("\t") }
    }

    @Test
    fun `header sniffing recognizes optional name header and kcal header`() {
        assertTrue(FoodPackValidator.hasHeaderRow(listOf("name") + List(25) { "h" }))
        assertTrue(FoodPackValidator.hasHeaderRow(row("anything", "not-a-number")))
        assertEquals(false, FoodPackValidator.hasHeaderRow(row("Real food")))
    }

    @Test
    fun `validator enforces 26 columns, non-empty rows and header sniff`() {
        val valid = assertIs<PackValidation.Valid>(FoodPackValidator.validate(pack()))
        assertEquals(2, valid.rowCount)
        assertTrue(valid.hasHeader)

        val short = row("Short").dropLast(1).joinToString("\t")
        val invalid = assertIs<PackValidation.Invalid>(FoodPackValidator.validate(short))
        assertTrue(invalid.reason.contains("26"), invalid.reason)

        val empty = assertIs<PackValidation.Invalid>(FoodPackValidator.validate(""))
        assertTrue(empty.reason.isNotBlank())
    }

    @Test
    fun `validator checks sha256 and row count from manifest`() {
        val tsv = pack()
        val digest = FoodPackValidator.sha256(tsv.toByteArray(Charsets.UTF_8))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FoodPackValidator.sha256("abc".toByteArray(Charsets.UTF_8)))

        assertIs<PackValidation.Valid>(
            FoodPackValidator.validate(tsv, expectedCount = 2, expectedSha256 = digest),
        )
        assertTrue(assertIs<PackValidation.Invalid>(
            FoodPackValidator.validate(tsv, expectedSha256 = "deadbeef"),
        ).reason.contains("checksum", ignoreCase = true))
        assertTrue(assertIs<PackValidation.Invalid>(
            FoodPackValidator.validate(tsv, expectedCount = 99),
        ).reason.contains("row count", ignoreCase = true))
    }

    @Test
    fun `manifest json parses pack entries`() {
        val manifest = FoodPackManifest.fromJson(
            """{"version":1,"packs":[{"id":"off-global","file":"off-global.tsv","size":123,"count":2,"sha256":"abc"}]}""",
        )
        assertEquals(1, manifest.version)
        assertEquals("off-global", manifest.packs.single().id)
        assertEquals("off-global.tsv", manifest.packs.single().file)
        assertEquals(2, manifest.packs.single().count)
        assertEquals("abc", manifest.packs.single().sha256)
        assertFails { FoodPackManifest.fromJson("{}") }
    }

    @Test
    fun `importing a validated pack adds foods and micros without deleting existing data`() {
        val store = newTestStore()
        val existing = store.food.insertCustomFood("Keep me", null, 1.0, 1.0, 1.0, 1.0, null, null, null, null, null)
        store.settings.set("theme", "dark")
        val manager = FoodPackManager(store.food, store.settings)
        val checksum = FoodPackValidator.sha256(pack().toByteArray(Charsets.UTF_8))

        assertNull(manager.installedPack())
        val result = manager.importValidatedPack(pack(), "off-global", "2026-11", checksum)

        val success = assertIs<PackInstallResult.Success>(result, (result as? PackInstallResult.Failure)?.reason ?: "")
        assertEquals(2, success.inserted)
        assertEquals(3, store.food.count())
        assertEquals("Keep me", store.food.byId(existing)?.name)
        assertTrue(store.food.recent().any { it.name == "Pack food A" })
        val inserted = store.food.recent().first { it.name == "Pack food A" }
        assertTrue(store.food.micros(inserted.id)?.contains("saturatedFat") == true)
        assertEquals("dark", store.settings.get("theme"))
        assertEquals("off-global", manager.installedPack()?.id)
        assertEquals("2026-11", manager.installedPack()?.version)
        assertEquals(checksum, manager.installedPack()?.sha256)
    }

    @Test
    fun `an invalid pack is rejected without touching food rows or settings`() {
        val store = newTestStore()
        store.food.insertCustomFood("Keep me", null, 1.0, 1.0, 1.0, 1.0, null, null, null, null, null)
        store.settings.set("theme", "dark")
        val manager = FoodPackManager(store.food, store.settings)

        val invalid = row("Bad").dropLast(1).joinToString("\t")
        val result = manager.importValidatedPack(invalid, "bad-pack", "1", null)

        assertIs<PackInstallResult.Failure>(result)
        assertEquals(1, store.food.count())
        assertEquals("Keep me", store.food.recent().single().name)
        assertEquals("dark", store.settings.get("theme"))
        assertNull(manager.installedPack())
    }
}
