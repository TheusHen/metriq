package com.metriq.app.barcode

import com.metriq.data.MetriqStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** A product parsed from the Open Food Facts v2 API, reduced to Metriq's food fields. */
data class OffProduct(
    val name: String,
    val brand: String?,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val servingLabel: String?,
    val servingGrams: Double?,
)

/** Explicit outcomes so the barcode screen can show an honest message for every failure. */
sealed interface LookupOutcome {
    data class Success(val product: OffProduct, val foodId: Long) : LookupOutcome
    data object NotFound : LookupOutcome
    data object NoNutrition : LookupOutcome
    data object InvalidBarcode : LookupOutcome
    data class NetworkError(val detail: String) : LookupOutcome
    data class Error(val message: String) : LookupOutcome
}

/**
 * Opt-in only: callers must ask the user before invoking this. The network request, JSON
 * parsing and database write all run on Dispatchers.IO; nothing touches the main thread.
 */
object OpenFoodFactsLookup {

    private const val ENDPOINT = "https://world.openfoodfacts.org/api/v2/product/"
    private const val FIELDS = "product_name,nutriments,serving_size,serving_quantity"
    private const val USER_AGENT = "Metriq/0.1 (Android food logging)"

    /** Normalize user or scanner input to the digits of an EAN/UPC code. */
    fun normalize(code: String): String = code.trim().replace(Regex("[\\s-]"), "")

    /** EAN-8, UPC-A and EAN-13 are the formats used on retail food packages. */
    fun isValid(code: String): Boolean {
        val digits = normalize(code)
        return digits.length in 8..14 && digits.all { it in '0'..'9' }
    }

    suspend fun lookupAndStore(code: String, store: MetriqStore): LookupOutcome =
        withContext(Dispatchers.IO) {
            val normalized = normalize(code)
            if (!isValid(normalized)) return@withContext LookupOutcome.InvalidBarcode

            // A previous lookup (or the bundled pack) already cached this barcode.
            val cached = store.food.byBarcode(normalized)
            if (cached != null) {
                return@withContext LookupOutcome.Success(
                    product = OffProduct(
                        name = cached.name,
                        brand = cached.brand,
                        calories = cached.calories,
                        protein = cached.protein,
                        fat = cached.fat,
                        carbs = cached.carbs,
                        servingLabel = cached.servingLabel,
                        servingGrams = cached.servingGrams,
                    ),
                    foodId = cached.id,
                )
            }

            val url = ENDPOINT + URLEncoder.encode(normalized, "UTF-8") + ".json?fields=$FIELDS"
            var connection: HttpURLConnection? = null
            val body = try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                    HttpURLConnection.HTTP_NOT_FOUND -> return@withContext LookupOutcome.NotFound
                    else -> return@withContext LookupOutcome.NetworkError(
                        "Server returned HTTP ${connection.responseCode}",
                    )
                }
            } catch (e: IOException) {
                return@withContext LookupOutcome.NetworkError(
                    e.message?.takeIf { it.isNotBlank() } ?: "sem ligação à rede",
                )
            } catch (e: Exception) {
                return@withContext LookupOutcome.Error(e.message ?: "erro inesperado na pesquisa")
            } finally {
                connection?.disconnect()
            }

            val root = try {
                JSONObject(body)
            } catch (_: Exception) {
                return@withContext LookupOutcome.Error("Resposta inválida do Open Food Facts")
            }
            if (root.optInt("status", 0) != 1) return@withContext LookupOutcome.NotFound
            val product = root.optJSONObject("product") ?: return@withContext LookupOutcome.NotFound

            val name = listOf("product_name", "product_name_en", "generic_name")
                .firstNotNullOfOrNull { key -> product.optString(key).takeIf { it.isNotBlank() } }
                ?.trim()
                ?: return@withContext LookupOutcome.Error("Product record has no name.")

            val n = product.optJSONObject("nutriments") ?: JSONObject()
            val calories = n.number("energy-kcal_100g", "energy-kcal_value", "energy-kcal")
                ?: n.number("energy-kj_100g")?.div(4.184)
                ?: return@withContext LookupOutcome.NoNutrition

            val parsed = OffProduct(
                name = name,
                brand = product.optString("brands").ifBlank { null },
                calories = calories,
                protein = n.number("proteins_100g", "proteins_value", "proteins") ?: 0.0,
                fat = n.number("fat_100g", "fat_value", "fat") ?: 0.0,
                carbs = n.number("carbohydrates_100g", "carbohydrates_value", "carbohydrates") ?: 0.0,
                servingLabel = product.optString("serving_size").ifBlank { null },
                servingGrams = product.opt("serving_quantity")?.asFiniteDouble(),
            )

            // insertCustomFood has no barcode parameter, so stamp it with a direct statement
            // after insertion. The unique index makes a second lookup of the same code a no-op.
            val id = try {
                store.food.insertCustomFood(
                    name = parsed.name,
                    brand = parsed.brand,
                    calories = parsed.calories,
                    protein = parsed.protein,
                    fat = parsed.fat,
                    carbs = parsed.carbs,
                    fiber = null,
                    sugar = null,
                    sodium = null,
                    servingLabel = parsed.servingLabel,
                    servingGrams = parsed.servingGrams,
                )
            } catch (e: Exception) {
                return@withContext LookupOutcome.Error(
                    "Could not save product: ${e.message ?: "database error"}",
                )
            }

            try {
                store.driver.execute(null, "UPDATE food SET barcode = ? WHERE id = ?", 2) {
                    bindString(0, normalized)
                    bindLong(1, id)
                }
            } catch (e: Exception) {
                // Do not leave a barcodeless duplicate behind if the stamp fails.
                try {
                    store.driver.execute(null, "DELETE FROM food WHERE id = ?", 1) {
                        bindLong(0, id)
                    }
                } catch (_: Exception) {
                    // Best effort cleanup; the error below is still reported to the user.
                }
                return@withContext LookupOutcome.Error(
                    "Could not save barcode: ${e.message ?: "database error"}",
                )
            }

            LookupOutcome.Success(parsed, id)
        }

    private fun Any.asFiniteDouble(): Double? = when (this) {
        is Number -> toDouble().takeIf { it.isFinite() && it > 0.0 }
        is String -> replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        else -> null
    }

    /** OFF sometimes returns numbers as strings; accept both and ignore junk. */
    private fun JSONObject.number(vararg keys: String): Double? {
        for (key in keys) {
            val value = opt(key) ?: continue
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
