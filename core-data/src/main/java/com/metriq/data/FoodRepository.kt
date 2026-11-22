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
        if (count() > 0L) {
            rebuildIndex()
            return
        }
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
                index(id, cols[0], cols[1], cols[2])
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
    fun rebuildIndex() {
        driver.execute(null, "DELETE FROM foodFts", 0)
        db.metriqQueries.selectAllFoodIds().executeAsList().forEach { food ->
            index(food.id, food.name, food.brand ?: "", food.category ?: "")
        }
    }

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
        index(id, name, brand ?: "", "custom")
        notifier?.notify(DataType.FOOD_LOG)
        return id
    }

    fun byBarcode(barcode: String) = db.metriqQueries.foodByBarcode(barcode).executeAsOneOrNull()

    fun byId(id: Long) = db.metriqQueries.foodById(id).executeAsOneOrNull()

    fun recent(limit: Long = 30) = db.metriqQueries.recentFood(limit).executeAsList()

    /**
     * FTS5 treats quotes, colons, dashes and words such as NEAR as operators. Keep only
     * word characters and quote every token so any user input is a harmless phrase query.
     */
    private fun ftsMatch(rawQuery: String): String = Regex("\\w+")
        .findAll(rawQuery)
        .joinToString(" ") { "\"${it.value}\"*" }

    fun search(rawQuery: String, limit: Int = 40): List<FoodHit> {
        val match = ftsMatch(rawQuery)
        if (match.isEmpty()) return emptyList()

        val sql = """
            SELECT f.id, f.name, f.brand, f.category, f.iconId, f.calories,
                   f.protein, f.fat, f.carbs, f.servingLabel, f.servingGrams
            FROM foodFts ft JOIN food f ON f.id = CAST(ft.foodId AS INTEGER)
            WHERE foodFts MATCH ?
            ORDER BY bm25(foodFts)
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
                2,
                {
                    bindString(0, match)
                    bindLong(1, limit.toLong())
                },
            ).value
        } catch (e: Exception) {
            // FTS5 signals malformed MATCH expressions with a platform-specific
            // SQLite exception (android.database on device, org.sqlite in JVM tests).
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

    private fun index(id: Long, name: String, brand: String, category: String) {
        driver.execute(
            null,
            "INSERT INTO foodFts(name, brand, category, foodId) VALUES (?, ?, ?, ?)",
            4,
            {
                bindString(0, name)
                bindString(1, brand)
                bindString(2, category)
                bindString(3, id.toString())
            },
        )
    }

    private fun lastInsertedId(): Long = driver.executeQuery(
        null,
        "SELECT last_insert_rowid()",
        { cursor -> QueryResult.Value(cursor.getLong(0) ?: 0L) },
        0,
    ).value
}

