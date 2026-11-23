package com.metriq.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.metriq.data.db.MetriqDatabase

data class RecipeDraft(
    val name: String,
    val servings: Int,
    val items: List<Pair<Long, Double>>,
)

class RecipeRepository(
    private val db: MetriqDatabase,
    private val notifier: DataChangeNotifier,
    private val driver: SqlDriver? = null,
) {

    fun all() = db.evolutionQueries.selectRecipes().executeAsList()

    fun items(recipeId: Long) = db.evolutionQueries.selectRecipeItems(recipeId).executeAsList()

    fun add(draft: RecipeDraft): Long {
        val totalGrams = draft.items.sumOf { it.second }
        var id = 0L
        db.transaction {
            db.evolutionQueries.insertRecipe(draft.name, draft.servings.toLong(), totalGrams, null)
            // Capture the id from the same statement/connection that performed the
            // insert. Loading "all recipes and taking the first" can pick up a row
            // inserted by a concurrent caller.
            id = if (driver != null) lastInsertedId(driver)
                 else db.evolutionQueries.selectRecipes().executeAsList().first().id
            draft.items.forEach { (foodId, grams) ->
                db.evolutionQueries.insertRecipeItem(id, foodId, grams)
            }
        }
        notifier.notify(DataType.RECIPE)
        return id
    }

    private fun lastInsertedId(driver: SqlDriver): Long = driver.executeQuery(
        null,
        "SELECT last_insert_rowid()",
        { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        },
        0,
    ).value
}

class ProgramRepository(private val db: MetriqDatabase) {

    fun current() = db.evolutionQueries.selectProgram().executeAsOneOrNull()

    fun save(style: String, weeklyKcal: Double, targetRatePercent: Double) {
        db.evolutionQueries.insertProgram(style, java.time.LocalDate.now().toString(), weeklyKcal, targetRatePercent, null)
    }
}
