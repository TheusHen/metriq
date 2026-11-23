package com.metriq.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.metriq.data.db.MetriqDatabase
import org.json.JSONArray
import org.json.JSONObject

data class FoodHit(
    val id: Long,
    val name: String,
    val brand: String?,
    val category: String?,
    val iconId: String?,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val servingLabel: String?,
    val servingGrams: Double?,
)

/**
 * Food search with FTS5. FTS is driven with raw SQL because SQLDelight's generator cannot
 * resolve implicit rowid columns on virtual tables.
 */
class FoodRepository(
    private val db: MetriqDatabase,
    private val driver: SqlDriver,
    private val notifier: DataChangeNotifier? = null,
) {

    companion object {
        /** Online hits are cached in a single settings value; no new schema is needed. */
        const val ONLINE_CACHE_KEY = "food_online_cache_v1"
        const val ONLINE_CACHE_MAX = 200
        const val ONLINE_HIT_LIMIT = 40
    }

    fun count(): Long = db.metriqQueries.countFood().executeAsOne()

    fun seedIfNeeded(assetText: String) {
        if (count() > 0L) return
        assetText.lineSequence()
            .map { it.split('\t') }
            .filter { it.size >= 11 && it[0].isNotBlank() }
            .filterNot { isHeaderRow(it) }
            .forEach { cols ->
                val extended = cols.size >= 26
                val offset = if (extended) 1 else 0
                db.metriqQueries.insertFood(
                    source = "base",
                    sourceId = cols[0],
                    barcode = if (extended) cols[3].ifBlank { null } else null,
                    name = cols[0],
                    brand = cols[1].ifBlank { null },
                    category = cols[2].ifBlank { null },
                    iconId = null,
                    calories = cols[3 + offset].toDoubleOrNull() ?: 0.0,
                    protein = cols[4 + offset].toDoubleOrNull() ?: 0.0,
                    fat = cols[5 + offset].toDoubleOrNull() ?: 0.0,
                    carbs = cols[6 + offset].toDoubleOrNull() ?: 0.0,
                    fiber = cols.getOrNull(7 + offset)?.toDoubleOrNull(),
                    sugar = cols.getOrNull(8 + offset)?.toDoubleOrNull(),
                    sodium = cols.getOrNull(9 + offset)?.toDoubleOrNull(),
                    servingLabel = cols.getOrNull(if (extended) 24 else 10)?.ifBlank { null },
                    servingGrams = cols.getOrNull(if (extended) 25 else 11)?.toDoubleOrNull(),
                    verified = 1L,
                    isCustom = 0L,
                )
                val id = lastInsertedId()
                if (extended) {
                    val names = listOf(
                        "saturatedFat", "transFat", "cholesterol", "potassium", "calcium",
                        "iron", "magnesium", "zinc", "vitaminA", "vitaminC", "vitaminD",
                        "vitaminB12", "omega3",
                    )
                    val pairs = names.mapIndexedNotNull { i, key ->
                        cols.getOrNull(11 + i)?.toDoubleOrNull()?.let { "\"$key\":$it" }
                    }
                    if (pairs.isNotEmpty()) {
                        db.evolutionQueries.insertFoodMicros(id, pairs.joinToString(",", "{", "}"))
                    }
                }
            }
    }
    /** Kept for source compatibility; search no longer depends on a separate index. */
    fun rebuildIndex() = Unit

    fun insertCustomFood(
        name: String,
        brand: String?,
        calories: Double,
        protein: Double,
        fat: Double,
        carbs: Double,
        fiber: Double?,
        sugar: Double?,
        sodium: Double?,
        servingLabel: String?,
        servingGrams: Double?,
    ): Long {
        db.metriqQueries.insertCustomFood(
            name, brand, calories, protein, fat, carbs, fiber, sugar, sodium, servingLabel, servingGrams,
        )
        val id = lastInsertedId()
        notifier?.notify(DataType.FOOD_LOG)
        return id
    }

    fun byBarcode(barcode: String) = db.metriqQueries.foodByBarcode(barcode).executeAsOneOrNull()

    fun byId(id: Long) = db.metriqQueries.foodById(id).executeAsOneOrNull()

    fun recent(limit: Long = 30) = db.metriqQueries.recentFood(limit).executeAsList()

    /** Canonical everyday foods from the bundled pack, not the newest imported products. */
    fun favorites(limit: Long = 6): List<FoodHit> = commonFoods(limit)

    fun commonFoods(limit: Long = 40): List<FoodHit> = db.metriqQueries.firstFoods(limit)
        .executeAsList()
        .map { food ->
            FoodHit(
                id = food.id,
                name = food.name,
                brand = food.brand,
                category = food.category,
                iconId = food.iconId,
                calories = food.calories,
                protein = food.protein,
                fat = food.fat,
                carbs = food.carbs,
                servingLabel = food.servingLabel,
                servingGrams = food.servingGrams,
            )
        }

    /**
     * Device-compatible food search. Android's system SQLite does not always ship the FTS5
     * extension (e.g. Samsung/One UI), so search uses plain LIKE over the food table instead.
     * Only word characters are kept, which also makes malformed input harmless.
     */
    fun search(rawQuery: String, limit: Int = 40): List<FoodHit> {
        val terms = Regex("\\w+").findAll(rawQuery)
            .map { it.value.lowercase() }
            .distinct()
            .take(6)
            .toList()
        if (terms.isEmpty()) return emptyList()

        val where = terms.joinToString(" AND ") {
            "(lower(f.name) LIKE ? OR lower(COALESCE(f.brand,'')) LIKE ? OR lower(COALESCE(f.category,'')) LIKE ?)"
        }
        val prefix = terms.first() + "%"
        val sql = """
            SELECT f.id, f.name, f.brand, f.category, f.iconId, f.calories,
                   f.protein, f.fat, f.carbs, f.servingLabel, f.servingGrams
            FROM food f
            WHERE $where
            ORDER BY (lower(f.name) LIKE ? OR lower(COALESCE(f.brand,'')) LIKE ?) DESC, f.name ASC
            LIMIT ?
        """.trimIndent()

        return try {
            driver.executeQuery(
                null,
                sql,
                { cursor ->
                    val out = ArrayList<FoodHit>()
                    while (cursor.next().value) out.add(cursor.toFoodHit())
                    QueryResult.Value(out)
                },
                terms.size * 3 + 3,
                {
                    var index = 0
                    terms.forEach { term ->
                        bindString(index++, "%$term%")
                        bindString(index++, "%$term%")
                        bindString(index++, "%$term%")
                    }
                    bindString(index++, prefix)
                    bindString(index++, prefix)
                    bindLong(index, limit.toLong())
                },
            ).value
        } catch (_: Exception) {
            // Search is best-effort: a bad query must never crash the screen.
            emptyList()
        }
    }

    /**
     * Unified search used by the multi-source search engine: bundled/online-imported
     * foods first, then the small cache of previously fetched online hits. Results
     * are de-duplicated by name+brand so the same food never appears twice.
     */
    fun searchAll(query: String, limit: Int = 40): List<FoodHit> {
        val local = search(query, limit)
        if (local.size >= limit) return local
        val cached = cachedOnline(query, limit - local.size)
        if (cached.isEmpty()) return local

        val seen = HashSet<String>()
        val combined = ArrayList<FoodHit>(local.size + cached.size)
        (local + cached).forEach { hit ->
            if (seen.add(cacheKey(hit))) combined.add(hit)
        }
        return combined
    }

    /** Store freshly fetched online hits, keeping the cache bounded and most-recent-first. */
    fun cacheOnline(hits: List<FoodHit>) {
        if (hits.isEmpty()) return
        val existing = decodeOnlineHits(db.metriqQueries.settingValue(ONLINE_CACHE_KEY).executeAsOneOrNull())
        val merged = (hits + existing)
            .filter { it.name.isNotBlank() }
            .distinctBy { cacheKey(it) }
            .take(ONLINE_CACHE_MAX)
        db.metriqQueries.setSetting(ONLINE_CACHE_KEY, encodeOnlineHits(merged))
    }

    /** Online hits from the cache whose name, brand or category matches the query terms. */
    fun cachedOnline(query: String, limit: Int = ONLINE_HIT_LIMIT): List<FoodHit> {
        val terms = Regex("\\w+").findAll(query)
            .map { it.value.lowercase() }
            .distinct()
            .take(6)
            .toList()
        if (terms.isEmpty()) return emptyList()
        val raw = db.metriqQueries.settingValue(ONLINE_CACHE_KEY).executeAsOneOrNull()
        return decodeOnlineHits(raw)
            .filter { hit ->
                val haystack = listOfNotNull(hit.name, hit.brand, hit.category)
                    .joinToString(" ").lowercase()
                terms.all { haystack.contains(it) }
            }
            .take(limit)
    }

    /** Online hits carry synthetic ids, so identity must come from name+brand only. */
    private fun cacheKey(hit: FoodHit): String =
        "${hit.name.trim().lowercase()}|${hit.brand?.trim()?.lowercase().orEmpty()}"

    private fun encodeOnlineHits(hits: List<FoodHit>): String {
        val array = JSONArray()
        hits.forEach { hit ->
            val item = JSONObject().apply {
                put("id", hit.id)
                put("name", hit.name)
                putNullable("brand", hit.brand)
                putNullable("category", hit.category)
                putNullable("iconId", hit.iconId)
                put("calories", hit.calories)
                put("protein", hit.protein)
                put("fat", hit.fat)
                put("carbs", hit.carbs)
                putNullable("servingLabel", hit.servingLabel)
                hit.servingGrams?.let { put("servingGrams", it) }
            }
            array.put(item)
        }
        return JSONObject().put("hits", array).toString()
    }

    private fun decodeOnlineHits(raw: String?): List<FoodHit> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONObject(raw).optJSONArray("hits") ?: return emptyList()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                FoodHit(
                    id = item.optLong("id"),
                    name = name,
                    brand = item.stringOrNull("brand"),
                    category = item.stringOrNull("category"),
                    iconId = item.stringOrNull("iconId"),
                    calories = item.optDouble("calories"),
                    protein = item.optDouble("protein"),
                    fat = item.optDouble("fat"),
                    carbs = item.optDouble("carbs"),
                    servingLabel = item.stringOrNull("servingLabel"),
                    servingGrams = if (item.has("servingGrams") && !item.isNull("servingGrams")) {
                        item.optDouble("servingGrams")
                    } else {
                        null
                    },
                )
            }
        } catch (_: Exception) {
            // A corrupt/legacy cache value must never break search.
            emptyList()
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (!value.isNullOrBlank()) put(key, value)
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).ifBlank { null }

    private fun SqlCursor.toFoodHit() = FoodHit(
        id = getLong(0)!!,
        name = getString(1).orEmpty(),
        brand = getString(2)?.ifBlank { null },
        category = getString(3)?.ifBlank { null },
        iconId = getString(4)?.ifBlank { null },
        calories = getDouble(5) ?: 0.0,
        protein = getDouble(6) ?: 0.0,
        fat = getDouble(7) ?: 0.0,
        carbs = getDouble(8) ?: 0.0,
        servingLabel = getString(9)?.ifBlank { null },
        servingGrams = getDouble(10),
    )

    fun micros(id: Long): String? = db.evolutionQueries.selectFoodMicros(id).executeAsOneOrNull()

    /** Mirrors common.py:read_pack: an optional "name" header must not be seeded as a food. */
    private fun isHeaderRow(cols: List<String>): Boolean {
        if (cols[0].equals("name", ignoreCase = true)) return true
        val kcal = cols.getOrNull(if (cols.size >= 26) 4 else 3)?.trim().orEmpty()
        return kcal.isNotEmpty() && kcal.toDoubleOrNull() == null
    }

    private fun lastInsertedId(): Long = driver.executeQuery(
        null,
        "SELECT last_insert_rowid()",
        { cursor ->
            // Android cursors start at position -1; the JVM test driver happens to
            // auto-position. Always advance before reading so both behave the same.
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        },
        0,
    ).value
}

