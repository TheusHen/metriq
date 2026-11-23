package com.metriq.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.metriq.data.db.MetriqDatabase
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodRepositoryTest {

    private fun emptyStore(): MetriqStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Deliberately NO foodFts table: Android's system SQLite may lack FTS5.
        // Search must work against the plain food table on every device.
        MetriqDatabase.Schema.create(driver)
        return MetriqStore(MetriqDatabase(driver), driver, DataChangeNotifier())
    }

    private fun store(): MetriqStore {
        val store = emptyStore()
        store.food.seedIfNeeded(File("src/main/assets/foods.tsv").readText())
        return store
    }

    @Test
    fun `seed loads the bundled pack`() {
        val store = store()
        assertTrue(store.food.count() >= 50)
    }

    @Test
    fun `prefix search finds foods`() {
        val store = store()
        val hits = store.food.search("ban")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.name.startsWith("Banana") })
    }

    @Test
    fun `multi word search matches brand and name`() {
        val store = store()
        val hits = store.food.search("chicken breast")
        assertTrue(hits.any { it.name.contains("Chicken breast") })
    }

    @Test
    fun `blank query returns nothing`() {
        assertTrue(store().food.search("  ").isEmpty())
    }

    @Test
    fun `ordinary prefix search still finds milk`() {
        val store = store()
        assertTrue(store.food.search("mil").any { it.name.startsWith("Milk") })
    }

    @Test
    fun `malformed fts input is sanitized instead of crashing search`() {
        val store = store()
        // An unbalanced quote is just a stray character: the word is still searchable.
        assertTrue(store.food.search("\"milk").any { it.name.contains("milk", ignoreCase = true) })
        // Operator-like input must never surface a SQLiteException to the UI.
        assertTrue(store.food.search("foo:").isEmpty())
        assertTrue(store.food.search("NEAR").isEmpty())
        assertTrue(store.food.search("-").isEmpty())
        assertTrue(store.food.search("").isEmpty())
        assertTrue(store.food.search("   ").isEmpty())
    }

    @Test
    fun `headered extended pack skips the header row`() {
        val store = emptyStore()
        val header = List(26) { "h$it" }.toMutableList().apply { this[0] = "name" }
        val row = List(26) { "" }.toMutableList().apply {
            this[0] = "Test Food"
            this[1] = "Acme"
            this[2] = "snack"
            this[3] = "1234567890"
            this[4] = "200"
            this[5] = "10"
            this[24] = "1 cup"
            this[25] = "240"
        }
        val pack = listOf(header, row).joinToString("\n") { it.joinToString("\t") }
        store.food.seedIfNeeded(pack)
        assertEquals(1, store.food.count())
        assertEquals("Test Food", store.food.recent().single().name)
        assertEquals(200.0, store.food.recent().single().calories)
    }

    @Test
    fun `custom food is indexed and searchable`() {
        val store = store()
        val id = store.food.insertCustomFood(
            name = "My special oats",
            brand = null,
            calories = 380.0,
            protein = 12.0,
            fat = 7.0,
            carbs = 65.0,
            fiber = 8.0,
            sugar = 1.0,
            sodium = 3.0,
            servingLabel = "1 bowl",
            servingGrams = 60.0,
        )
        assertEquals(id, store.food.search("special").firstOrNull()?.id)
    }

    @Test
    fun `bundled pack includes barcoded products for offline scanning`() {
        val store = store()
        val hit = store.food.byBarcode("5449000054227")
        assertTrue(hit != null, "Expected a bundled product with barcode 5449000054227")
        assertEquals("Coca-Cola Original Taste", hit!!.name)
    }

    @Test
    fun `barcode lookup works after inserting a branded food`() {
        val store = store()
        store.db.metriqQueries.insertFood(
            source = "off", sourceId = "1", barcode = "7891234567890",
            name = "Test bar", brand = "Acme", category = "snack", iconId = null,
            calories = 500.0, protein = 7.0, fat = 20.0, carbs = 70.0,
            fiber = 1.0, sugar = 30.0, sodium = 200.0,
            servingLabel = "1 unit", servingGrams = 40.0, verified = 0L, isCustom = 0L,
        )
        assertTrue(store.food.byBarcode("7891234567890")?.name == "Test bar")
    }

    private fun onlineHit(id: Long, name: String) = FoodHit(
        id = id, name = name, brand = "Online Brand", category = "dairy", iconId = null,
        calories = 90.0, protein = 5.0, fat = 2.0, carbs = 12.0,
        servingLabel = "1 cup", servingGrams = 200.0,
    )

    @Test
    fun `cacheOnline hits are found by searchAll and cachedOnline`() {
        val store = store()
        assertTrue(store.food.search("online test yogurt").isEmpty())
        store.food.cacheOnline(listOf(onlineHit(-1L, "Online Test Yogurt")))

        val hits = store.food.searchAll("online test yogurt")
        assertTrue(hits.any { it.id == -1L && it.name == "Online Test Yogurt" })
        assertEquals(1, store.food.cachedOnline("online test yogurt").size)
        assertTrue(store.food.cachedOnline("unrelated").isEmpty())
    }

    @Test
    fun `online cache persists across repositories and is bounded to 200`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MetriqDatabase.Schema.create(driver)
        val db = MetriqDatabase(driver)
        val writer = MetriqStore(db, driver, DataChangeNotifier())
        writer.food.cacheOnline((1..250).map { onlineHit(-it.toLong(), "Online food $it") })

        val reader = MetriqStore(db, driver, DataChangeNotifier())
        val raw = reader.db.metriqQueries
            .settingValue(FoodRepository.ONLINE_CACHE_KEY).executeAsOneOrNull()!!
        val array = JSONObject(raw).getJSONArray("hits")
        assertEquals(FoodRepository.ONLINE_CACHE_MAX, array.length())
        assertEquals(-1L, array.getJSONObject(0).getLong("id"))
        assertTrue((0 until array.length()).none { array.getJSONObject(it).getLong("id") == -250L })
        assertEquals(40, reader.food.cachedOnline("online food").size)
    }

    @Test
    fun `searchAll does not duplicate a hit that is both local and cached`() {
        val store = store()
        val local = store.food.search("banana").first()
        store.food.cacheOnline(listOf(local.copy(id = -999L)))
        assertEquals(1, store.food.searchAll("banana").count { it.name == local.name })
    }

    @Test
    fun `corrupt online cache never breaks search`() {
        val store = store()
        store.db.metriqQueries.setSetting(FoodRepository.ONLINE_CACHE_KEY, "not-json")
        assertTrue(store.food.cachedOnline("banana").isEmpty())
        assertTrue(store.food.searchAll("banana").any { it.name.startsWith("Banana") })
    }
}
