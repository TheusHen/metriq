package com.metriq.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.metriq.data.db.MetriqDatabase

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
    fun favorites(limit: Long = 6): List<FoodHit> = db.metriqQueries.firstFoods(limit)
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

