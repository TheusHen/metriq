package com.metriq.data

import com.metriq.data.db.MetriqDatabase

data class RecipeDraft(
    val name: String,
    val servings: Int,
    val items: List<Pair<Long, Double>>,
)

class RecipeRepository(private val db: MetriqDatabase, private val notifier: DataChangeNotifier) {

    fun all() = db.evolutionQueries.selectRecipes().executeAsList()

    fun items(recipeId: Long) = db.evolutionQueries.selectRecipeItems(recipeId).executeAsList()

    fun add(draft: RecipeDraft): Long {
        val totalGrams = draft.items.sumOf { it.second }
        db.evolutionQueries.insertRecipe(draft.name, draft.servings.toLong(), totalGrams, null)
        val id = db.evolutionQueries.selectRecipes().executeAsList().first().id
        draft.items.forEach { (foodId, grams) ->
            db.evolutionQueries.insertRecipeItem(id, foodId, grams)
        }
        notifier.notify(DataType.RECIPE)
        return id
    }
}

class ProgramRepository(private val db: MetriqDatabase) {

    fun current() = db.evolutionQueries.selectProgram().executeAsOneOrNull()

    fun save(style: String, weeklyKcal: Double, targetRatePercent: Double) {
        db.evolutionQueries.insertProgram(style, java.time.LocalDate.now().toString(), weeklyKcal, targetRatePercent, null)
    }
}
