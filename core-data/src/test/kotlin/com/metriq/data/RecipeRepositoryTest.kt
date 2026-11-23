package com.metriq.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeRepositoryTest {

    @Test
    fun `add returns its own recipe id even when another insert interleaves`() {
        val store = newTestStore()
        val foodId = store.food.insertCustomFood("Ingredient", null, 100.0, 5.0, 1.0, 10.0, null, null, null, null, null)

        // Deterministically reproduce the race: any insert into `recipe` is followed by
        // another insert, so "read the newest recipe" returns somebody else's id.
        val driver = store.driver
        driver.execute(
            null,
            "CREATE TRIGGER recipe_race AFTER INSERT ON recipe BEGIN " +
                "INSERT INTO recipe(name, servings, totalGrams) VALUES ('Intruder', 1, 0); END",
            0,
        )

        val id = store.recipe.add(RecipeDraft("My stew", 2, listOf(foodId to 200.0)))

        val recipe = store.recipe.all().firstOrNull { it.id == id }
        assertEquals("My stew", recipe?.name)
        assertEquals(200.0, store.recipe.items(id).single().grams)
    }

    @Test
    fun `delete removes a recipe and only its own items`() = runBlocking {
        val store = newTestStore()
        val a = store.food.insertCustomFood("A", null, 10.0, 1.0, 1.0, 1.0, null, null, null, null, null)
        val b = store.food.insertCustomFood("B", null, 20.0, 1.0, 1.0, 1.0, null, null, null, null, null)
        val doomed = store.recipe.add(RecipeDraft("Doomed", 1, listOf(a to 100.0)))
        val kept = store.recipe.add(RecipeDraft("Kept", 1, listOf(b to 200.0)))
        val watcher = DataChangeNotifier()
        val recipe = RecipeRepository(store.db, watcher, store.driver)

        recipe.delete(doomed)

        assertEquals(listOf(kept), store.recipe.all().map { it.id })
        assertTrue(store.recipe.items(doomed).isEmpty())
        assertEquals(listOf(b), store.recipe.items(kept).map { it.foodId })
        val event = withTimeout(1000) { watcher.events.first() }
        assertEquals(DataType.RECIPE, event.type)
    }

    @Test
    fun `delete rolls back both tables if an item deletion fails`() {
        val store = newTestStore()
        val foodId = store.food.insertCustomFood("A", null, 10.0, 1.0, 1.0, 1.0, null, null, null, null, null)
        val id = store.recipe.add(RecipeDraft("Doomed", 1, listOf(foodId to 100.0)))
        store.driver.execute(
            null,
            "CREATE TRIGGER block_item_delete BEFORE DELETE ON recipeItem BEGIN " +
                "SELECT RAISE(ABORT, 'blocked'); END",
            0,
        )

        val outcome = runCatching { store.recipe.delete(id) }

        assertTrue(outcome.isFailure)
        assertEquals(1, store.recipe.all().size)
        assertEquals(1, store.recipe.items(id).size)
    }

    @Test
    fun `multiple recipes keep their own items`() {
        val store = newTestStore()
        val a = store.food.insertCustomFood("A", null, 10.0, 1.0, 1.0, 1.0, null, null, null, null, null)
        val b = store.food.insertCustomFood("B", null, 20.0, 1.0, 1.0, 1.0, null, null, null, null, null)

        val first = store.recipe.add(RecipeDraft("First", 1, listOf(a to 100.0)))
        val second = store.recipe.add(RecipeDraft("Second", 1, listOf(b to 200.0)))

        assertEquals(listOf(a), store.recipe.items(first).map { it.foodId })
        assertEquals(listOf(b), store.recipe.items(second).map { it.foodId })
    }
}
