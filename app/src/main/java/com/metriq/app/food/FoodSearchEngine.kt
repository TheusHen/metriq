package com.metriq.app.food

import com.metriq.app.AppGraph
import com.metriq.data.FoodHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Multi-source food search: bundled pack -> cached online hits -> Open Food Facts.
 *
 * All work runs on [Dispatchers.IO]. Short queries and offline devices degrade to
 * local results only. No API key is needed and nothing personal is logged.
 */
object FoodSearchEngine {

    private const val ENDPOINT = "https://world.openfoodfacts.org/cgi/search.pl"
    private const val FIELDS = "code,product_name,nutriments,serving_size,serving_quantity"
    private const val USER_AGENT = "Metriq/0.1 (Android food search; open data)"
    private const val MIN_ONLINE_LENGTH = 3

    /** Hits plus whether the live lookup was attempted and failed, so the UI can offer a retry. */
    data class Outcome(val hits: List<FoodHit>, val onlineFailed: Boolean)

    suspend fun search(query: String): List<FoodHit> = searchWithOutcome(query).hits

    suspend fun searchWithOutcome(query: String): Outcome = withContext(Dispatchers.IO) {
        val store = runCatching { AppGraph.store }.getOrNull()
            ?: return@withContext Outcome(emptyList(), onlineFailed = false)

        // 1) local pack + 2) previously cached online hits.
        val local = store.food.searchAll(query)
        val term = query.trim()
        if (term.length < MIN_ONLINE_LENGTH) return@withContext Outcome(local, onlineFailed = false)

        // 3) live Open Food Facts search, best effort: offline means local only.
        val online = fetchOnline(term)
            ?: return@withContext Outcome(local, onlineFailed = true)
        if (online.isEmpty()) return@withContext Outcome(local, onlineFailed = false)
        store.food.cacheOnline(online)
        Outcome(store.food.searchAll(query), onlineFailed = false)
    }

    private fun buildUrl(term: String): String = buildList {
        add("search_terms" to term)
        add("json" to "1")
        add("page_size" to "10")
        add("fields" to FIELDS)
    }.joinToString("&", prefix = "$ENDPOINT?") { (key, value) ->
        "$key=${URLEncoder.encode(value, "UTF-8")}"
    }

    /** Null means the request failed; an empty list means the service had no matches. */
    private fun fetchOnline(term: String): List<FoodHit>? {
        var connection: HttpURLConnection? = null
        val body = try {
            connection = (URL(buildUrl(term)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                else -> return null
            }
        } catch (_: IOException) {
            return null // no network: caller keeps local results and can offer a retry
        } catch (_: Exception) {
            return null
        } finally {
            connection?.disconnect()
        }
        return parseResponse(body)
    }

    /** Pure parser, kept internal so it can be tested with a JSON fixture, no network. */
    internal fun parseResponse(body: String): List<FoodHit> {
        val products = try {
            JSONObject(body).optJSONArray("products") ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        val hits = ArrayList<FoodHit>()
        for (index in 0 until products.length()) {
            val product = products.optJSONObject(index) ?: continue
            val name = product.text("product_name") ?: continue
            val nutrients = product.optJSONObject("nutriments") ?: JSONObject()
            val kcal = nutrients.number("energy-kcal_100g", "energy-kcal_value", "energy-kcal")
                ?: nutrients.number("energy-kj_100g")?.div(4.184)
                ?: continue // a food without energy is not useful for logging

            val code = product.text("code")
            // Synthetic negative ids keep cached hits distinct from database foods.
            val hash = (code ?: name).hashCode().toLong()
            val id = if (hash == Long.MIN_VALUE) -1L else -kotlin.math.abs(hash) - 1L
            hits.add(
                FoodHit(
                    id = id,
                    name = name,
                    brand = product.text("brands"),
                    category = null,
                    iconId = null,
                    calories = kcal,
                    protein = nutrients.number("proteins_100g", "proteins_value", "proteins") ?: 0.0,
                    fat = nutrients.number("fat_100g", "fat_value", "fat") ?: 0.0,
                    carbs = nutrients.number("carbohydrates_100g", "carbohydrates_value", "carbohydrates") ?: 0.0,
                    servingLabel = product.text("serving_size"),
                    servingGrams = product.number("serving_quantity"),
                ),
            )
        }
        return hits
    }

    private fun JSONObject.text(key: String): String? {
        val value = opt(key)
        if (value == null || value === JSONObject.NULL) return null
        val text = value.toString().trim()
        return text.takeIf { it.isNotEmpty() && it != "null" }
    }

    /** OFF sometimes returns numbers as strings and sometimes uses a decimal comma. */
    private fun JSONObject.number(vararg keys: String): Double? {
        for (key in keys) {
            val value = opt(key)
            if (value == null || value === JSONObject.NULL) continue
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(',', '.').lowercase(Locale.ROOT).toDoubleOrNull()
                else -> null
            }
            if (number != null && number.isFinite() && number >= 0.0) return number
        }
        return null
    }
}
