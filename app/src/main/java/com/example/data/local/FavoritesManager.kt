package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.data.model.FoodEntryType
import com.example.data.model.FoodIngredient
import com.example.data.model.MealItem
import com.example.data.model.SwedishFoodDatabase
import com.example.data.model.SwedishFoodEntry
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class FrequentMealSummary(
    val canonicalKey: String,
    val displayName: String,
    val count: Int,
    val latestMeal: MealItem
)

/**
 * Local manager for user-pinned Favorite foods/meals, scanned nutrition-label products,
 * and derivation of top recurring & recent logged foods.
 */
object FavoritesManager {

    private const val TAG = "FavoritesManager"
    private const val PREFS_NAME = "zevra_quick_foods_prefs"
    private const val KEY_FAVORITE_FOODS = "favorite_foods_json"
    private const val KEY_SCANNED_PRODUCTS = "scanned_products_json"

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    private val foodListAdapter by lazy {
        val listType = Types.newParameterizedType(List::class.java, SwedishFoodEntry::class.java)
        moshi.adapter<List<SwedishFoodEntry>>(listType)
    }

    private val _favorites = MutableStateFlow<List<SwedishFoodEntry>>(emptyList())
    val favorites: StateFlow<List<SwedishFoodEntry>> = _favorites.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    val appCtx = context.applicationContext
                    _favorites.value = readList(appCtx, KEY_FAVORITE_FOODS)
                    hydrateScannedProducts(appCtx)
                    loaded = true
                }
            }
        }
    }

    fun hydrateScannedProducts(context: Context) {
        val scanned = readList(context.applicationContext, KEY_SCANNED_PRODUCTS)
        if (scanned.isNotEmpty()) {
            SwedishFoodDatabase.addDynamicFoods(scanned, fromAi = true)
        }
    }

    fun getFavorites(context: Context): List<SwedishFoodEntry> {
        ensureLoaded(context)
        return _favorites.value
    }

    fun isFavorite(context: Context, food: SwedishFoodEntry): Boolean {
        ensureLoaded(context)
        val normKey = normalizeFoodName(food.displayName)
        return _favorites.value.any {
            it.id == food.id || normalizeFoodName(it.displayName) == normKey
        }
    }

    fun isMealFavorite(context: Context, meal: MealItem): Boolean {
        ensureLoaded(context)
        val normKey = normalizeFoodName(meal.name)
        return _favorites.value.any { normalizeFoodName(it.displayName) == normKey }
    }

    fun toggleFavoriteFood(context: Context, food: SwedishFoodEntry): Boolean {
        ensureLoaded(context)
        val appCtx = context.applicationContext
        val normKey = normalizeFoodName(food.displayName)
        val current = _favorites.value.toMutableList()
        val existingIdx = current.indexOfFirst {
            it.id == food.id || normalizeFoodName(it.displayName) == normKey
        }
        val added: Boolean
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
            added = false
        } else {
            current.add(0, food)
            added = true
        }
        _favorites.value = current
        writeList(appCtx, KEY_FAVORITE_FOODS, current)
        return added
    }

    fun toggleFavoriteMeal(context: Context, meal: MealItem): Boolean {
        val asEntry = mealToFoodEntry(meal)
        return toggleFavoriteFood(context, asEntry)
    }

    fun saveScannedProduct(context: Context, product: SwedishFoodEntry) {
        val appCtx = context.applicationContext
        val current = readList(appCtx, KEY_SCANNED_PRODUCTS).toMutableList()
        current.removeAll {
            it.id == product.id ||
                (!product.barcode.isNullOrBlank() && it.barcode == product.barcode) ||
                normalizeFoodName(it.displayName) == normalizeFoodName(product.displayName)
        }
        current.add(0, product)
        val capped = current.take(100)
        writeList(appCtx, KEY_SCANNED_PRODUCTS, capped)
        SwedishFoodDatabase.addDynamicFood(product, fromAi = true)
    }

    /**
     * Converts a logged [MealItem] into a reusable [SwedishFoodEntry] with accurate per-100g
     * values and standard portion weight so it can be edited via PortionQuantityConfigSheet or logged in 1 tap.
     */
    fun mealToFoodEntry(meal: MealItem): SwedishFoodEntry {
        val cleanName = stripPortionSuffix(meal.name).ifBlank { meal.name }
        val ingredients = meal.getParsedIngredients()
        val totalGrams = ingredients.sumOf { it.amountGrams }.takeIf { it > 0 } ?: 100
        val factorTo100 = 100.0 / totalGrams.coerceAtLeast(1).toDouble()

        val mealCategoryLabel = when (meal.mealType) {
            "frukost" -> "Frukost"
            "lunch" -> "Lunch"
            "middag" -> "Middag"
            "mellanmal" -> "Mellanmål"
            else -> "Sparad måltid"
        }

        return SwedishFoodEntry(
            id = "fav_meal_${normalizeFoodName(cleanName).hashCode()}",
            name = cleanName,
            brand = null,
            category = "Favorit • $mealCategoryLabel",
            itemType = FoodEntryType.MEAL,
            caloriesPer100g = (meal.calories * factorTo100).roundToInt().coerceIn(0, 1200),
            proteinPer100g = ((meal.protein * factorTo100) * 10.0).roundToInt() / 10.0,
            carbsPer100g = ((meal.carbs * factorTo100) * 10.0).roundToInt() / 10.0,
            fatPer100g = ((meal.fat * factorTo100) * 10.0).roundToInt() / 10.0,
            fiberPer100g = ((meal.fiber * factorTo100) * 10.0).roundToInt() / 10.0,
            sugarPer100g = ((meal.sugar * factorTo100) * 10.0).roundToInt() / 10.0,
            saltPer100g = ((meal.salt * factorTo100) * 100.0).roundToInt() / 100.0,
            standardPortionGrams = totalGrams.coerceIn(10, 2000),
            standardPortionDescription = "1 sparad portion ($totalGrams g)"
        )
    }

    /**
     * Groups logged meals into Top N most frequent and Top N most recent unique entries.
     */
    fun computeFrequentAndRecentMeals(
        allMeals: List<MealItem>,
        maxFrequent: Int = 15,
        maxRecent: Int = 15
    ): Pair<List<FrequentMealSummary>, List<MealItem>> {
        if (allMeals.isEmpty()) return emptyList<FrequentMealSummary>() to emptyList()

        val chronological = allMeals.sortedWith(
            compareByDescending<MealItem> { it.date }
                .thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
        )

        val grouped = linkedMapOf<String, MutableList<MealItem>>()
        chronological.forEach { meal ->
            val key = normalizeFoodName(meal.name)
            if (key.isNotBlank()) {
                grouped.getOrPut(key) { mutableListOf() }.add(meal)
            }
        }

        val frequent = grouped.entries
            .map { (key, list) ->
                val latest = list.first()
                FrequentMealSummary(
                    canonicalKey = key,
                    displayName = stripPortionSuffix(latest.name).ifBlank { latest.name },
                    count = list.size,
                    latestMeal = latest
                )
            }
            .sortedWith(
                compareByDescending<FrequentMealSummary> { it.count }
                    .thenByDescending { it.latestMeal.date }
                    .thenByDescending { it.latestMeal.createdAt?.toDate()?.time ?: 0L }
            )
            .take(maxFrequent)

        val recent = grouped.values
            .map { it.first() }
            .take(maxRecent)

        return frequent to recent
    }

    fun normalizeFoodName(rawName: String): String {
        return stripPortionSuffix(rawName)
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    fun stripPortionSuffix(rawName: String): String {
        val trimmed = rawName.trim()
        // Strips trailing parenthetical portion labels generated by ManualFoodEntryScreen, e.g. "Havregryn (1 dl (35 g))"
        val idx = trimmed.lastIndexOf(" (")
        if (idx > 2 && trimmed.endsWith(")")) {
            val suffix = trimmed.substring(idx + 2, trimmed.length - 1).lowercase()
            val looksLikePortion = suffix.contains("portion") ||
                suffix.endsWith(" g") ||
                suffix.endsWith("g)") ||
                suffix.contains(" dl") ||
                suffix.contains(" ml") ||
                suffix.contains(" msk") ||
                suffix.contains(" tsk") ||
                suffix.contains(" hg") ||
                suffix.contains(" kg") ||
                suffix.endsWith(" st")
            if (looksLikePortion) {
                return trimmed.substring(0, idx).trim()
            }
        }
        return trimmed
    }

    private fun readList(context: Context, key: String): List<SwedishFoodEntry> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(key, null).orEmpty()
            if (json.isBlank()) return emptyList()
            foodListAdapter.fromJson(json).orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading $key", e)
            emptyList()
        }
    }

    private fun writeList(context: Context, key: String, items: List<SwedishFoodEntry>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = foodListAdapter.toJson(items)
            prefs.edit { putString(key, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing $key", e)
        }
    }
}
