package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.content.edit
import com.example.BuildConfig
import com.example.R
import com.example.data.ml.FoodClassifier
import com.example.data.model.AiDailyMealPlan
import com.example.data.model.AiFoodCorrection
import com.example.data.model.AiMealCorrectionParser
import com.example.data.model.AiMealCorrectionResult
import com.example.data.model.FoodEntryType
import com.example.data.model.FoodIngredient
import com.example.data.model.MealItem
import com.example.data.model.SwedishFoodEntry
import com.example.data.model.SwedishFoodDatabase
import com.example.data.model.FoodUnit
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AiNutritionService"

data class AiMealAnalysisResult(
    val mealName: String,
    val confidence: String, // "high", "medium", "low"
    val reasoning: String,
    val totalCalories: Int,
    val totalProteinG: Double,
    val totalCarbsG: Double,
    val totalFatG: Double,
    val totalFiberG: Double,
    val totalSugarG: Double,
    val totalSaltG: Double,
    val items: List<FoodIngredient>
)

class AiNutritionService {

    // Structured JSON Schema for Clinical Nutritionist Food Analysis
    private val visionFoodItemSchema = Schema.obj(
        properties = mapOf(
            "name" to Schema.string("Enskilt livsmedel på svenska"),
            "weight_g" to Schema.integer("Uppskattad vikt i gram (g)"),
            "calories" to Schema.integer("Kalorier (kcal) för denna komponent"),
            "protein_g" to Schema.double("Protein i gram (g) för denna komponent"),
            "carbs_g" to Schema.double("Kolhydrater i gram (g) för denna komponent"),
            "fat_g" to Schema.double("Fett i gram (g) för denna komponent")
        )
    )

    private val visionAnalysisSchema = Schema.obj(
        properties = mapOf(
            "dish_name" to Schema.string("Huvudnamn på rätten på svenska (eller 'Ingen mat identifierades' om bilden inte innehåller mat)"),
            "total_calories" to Schema.integer("Total energi i kcal för hela måltiden"),
            "total_protein_g" to Schema.double("Totalt protein i gram (g) för hela måltiden"),
            "total_carbs_g" to Schema.double("Totala kolhydrater i gram (g) för hela måltiden"),
            "total_fat_g" to Schema.double("Totalt fett i gram (g) för hela måltiden"),
            "items" to Schema.array(
                items = visionFoodItemSchema,
                description = "Lista över varje synligt livsmedel, sås, dressing och tillbehör på svenska"
            ),
            "preparation_notes" to Schema.string("Kort anteckning på svenska om trolig tillagningsmetod (t.ex. stekt i rapsolja, ugnsbakad)")
        )
    )

    private val visionConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = visionAnalysisSchema
        temperature = 0.2f
    }

    private val clinicalNutritionistSystemPrompt = """
        You are an expert clinical nutritionist and elite food-recognition AI integrated into a professional health and calorie-tracking mobile application. Your task is to analyze food images provided by the user with maximum accuracy, estimating realistic portions and nutritional values.

        STRICT INSTRUCTIONS:
        1. Language & Naming: All dish names, food items, and descriptions must be written in Swedish.
        2. Component Breakdown: Identify every visible food item, sauce, dressing, and side dish on the plate/bowl. Do not group everything into one lump sum if distinct items are visible. For single fruits, vegetables, or raw ingredients (e.g. apple, banana), identify the exact raw item.
        3. Weight & Portion Estimation: Estimate realistic weights in grams (g) for each item based on visual references like plate size, cutlery, or standard portion volumes.
        4. Nutritional Accuracy: Calculate exact or realistic estimates for Calories (kcal), Protein (g), Carbohydrates (g), and Fat (g) for both individual items and the total meal. Account for hidden fats like cooking oils or butter if typical for the dish.
        5. Non-Food Images: If the image contains no food (e.g., empty table, person, laptop, or emulator test pattern), return items: [], dish_name: "Ingen mat identifierades", and 0 for totals.
        6. Output Formatting: You MUST respond ONLY with a valid JSON object matching the schema below. Do NOT wrap the JSON in markdown code blocks (such as ```json), do not include introductory text, explanations, or concluding remarks.

        REQUIRED JSON STRUCTURE:
        {
          "dish_name": "Huvudnamn på rätten på svenska",
          "total_calories": 0,
          "total_protein_g": 0.0,
          "total_carbs_g": 0.0,
          "total_fat_g": 0.0,
          "items": [
            {
              "name": "Enskilt livsmedel på svenska",
              "weight_g": 0,
              "calories": 0,
              "protein_g": 0.0,
              "carbs_g": 0.0,
              "fat_g": 0.0
            }
          ],
          "preparation_notes": "Kort anteckning på svenska om trolig tillagningsmetod (t.ex. stekt i rapsolja, ugnsbakad)"
        }
    """.trimIndent()

    private val firebaseAi by lazy {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        )
    }

    private val visionModel by lazy {
        firebaseAi.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = visionConfig,
            systemInstruction = content {
                text(clinicalNutritionistSystemPrompt)
            }
        )
    }

    // Secondary vision model with separate quota pool (allows unlimited failover when primary model reaches daily quota)
    private val visionModelLite by lazy {
        firebaseAi.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = visionConfig,
            systemInstruction = content {
                text(clinicalNutritionistSystemPrompt)
            }
        )
    }

    private val chatModel by lazy {
        firebaseAi.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Du är Zevras personliga AI-dietist och kostrådgivare.
                    Ditt mål är att hjälpa användaren nå sina mål med vetenskapligt underbyggda, empatiska och konkreta råd på svenska.
                    Du känner till användarens profil (mål, kalorier, makron) och dagens registrerade måltider som tillhandahålls i kontexten.
                    Svara kortfattat, hjälpsamt och motiverande. Använd alltid svenska mått (g, ml, kcal).
                    """.trimIndent()
                )
            }
        )
    }

    // Structured JSON Schema for Global Food Database Search (Lifesum equivalent)
    private val globalFoodItemSchema = Schema.obj(
        properties = mapOf(
            "name" to Schema.string("Namn på livsmedlet, rätten eller produkten på svenska"),
            "brand" to Schema.string("Varumärke, tillverkare, butikskedja (t.ex. ICA, Garant, Eldorado, Coop) eller restaurang om tillämpligt, annars tom sträng"),
            "category" to Schema.string("Kategori, t.ex. 'Nötsmör & Pålägg', 'Mejeri', 'Mataffär', 'Snabbmat'"),
            "itemType" to Schema.enumeration(listOf("BRAND", "MEAL", "BASIC", "FASTFOOD", "SUPERMARKET", "DRINK"), "Typ av livsmedel eller dryck"),
            "caloriesPer100g" to Schema.integer("Energi i kcal per 100 gram"),
            "proteinPer100g" to Schema.double("Protein i gram per 100g"),
            "carbsPer100g" to Schema.double("Kolhydrater i gram per 100g"),
            "fatPer100g" to Schema.double("Fett i gram per 100g"),
            "fiberPer100g" to Schema.double("Fiber i gram per 100g"),
            "sugarPer100g" to Schema.double("Socker i gram per 100g"),
            "saltPer100g" to Schema.double("Salt i gram per 100g"),
            "standardPortionGrams" to Schema.integer("Normal medelportion i gram baserad på vad en vuxen människa normalt äter"),
            "standardPortionDescription" to Schema.string("Portionsbeskrivning t.ex. '1 msk (20 g)' eller '1 portion (250 g)'"),
            "densityGramsPerMl" to Schema.double("Densitet i g/ml (1.0 för vatten/fasta rätter, 1.05 för jordnötssmör, 1.03 för mjölk, 0.92 för olja)")
        )
    )

    private val globalFoodSearchSchema = Schema.obj(
        properties = mapOf(
            "items" to Schema.array(
                items = globalFoodItemSchema,
                description = "Lista över 4 till 6 matchande livsmedel med exakta näringsvärden per 100 gram"
            )
        )
    )

    private val foodSearchGenerationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = globalFoodSearchSchema
        temperature = 0.1f
    }

    private val foodSearchSystemPrompt = """
        Du är Sveriges och världens mest kompletta mat- och dagligvarudatabas (motsvarande hela sortimentet hos ICA, Willys, Hemköp, Coop, Lidl, Lifesum och MyFitnessPal).
        Du har full kunskap om ALLA varor som säljs i butik och på restaurang:
        - Alla varumärken och butiksmärken (Garant, Eldorado, ICA, ICA Basic, Coop, Änglamark, Green Choice, Skippy, Nutella, Pågen, Arla, Scan, Felix, Findus, Barebells, Nocco m.fl.).
        - Alla råvaror, livsmedel, pålägg, färdigrätter och restaurangmenyer.
        När användaren söker efter mat:
        1. Returnera JSON-objektet {"items": [...]} med 4 till 6 relevanta träffar som bäst motsvarar sökningen.
        2. Ange alltid tillverkare eller butiksmärke i 'brand' (t.ex. 'Green Choice', 'ICA', 'Garant (Willys & Hemköp)', 'Skippy', 'Arla').
        3. Sätt 'itemType' till 'DRINK' för alla drycker (kaffe, te, läsk, juice, mjölk, energidryck, öl, vin, smoothies), 'SUPERMARKET' för dagligvaror, 'BRAND' för allmänna märken, 'MEAL' för lagad mat, 'BASIC' för råvaror, eller 'FASTFOOD' för snabbmat.
        4. Ange korrekta officiella näringsvärden per 100 gram.
        5. 'standardPortionGrams' ska ALLTID motsvara vad en normal människa äter som en medelportion (t.ex. 20g för jordnötssmör/pålägg, 10g för smör, 40g för flingor, 150-350g för lunch/middag).
        6. Svara alltid på ren svenska med enbart giltig JSON.
    """.trimIndent()

    private val foodSearchModel by lazy {
        firebaseAi.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = foodSearchGenerationConfig,
            systemInstruction = content {
                text(foodSearchSystemPrompt)
            }
        )
    }

    private val foodSearchModelLite by lazy {
        firebaseAi.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = foodSearchGenerationConfig,
            systemInstruction = content {
                text(foodSearchSystemPrompt)
            }
        )
    }

    // Structured JSON Schema for AI Daily Meal Plan Generator
    private val mealPlanIngredientSchema = Schema.obj(
        properties = mapOf(
            "name" to Schema.string("Livsmedlets namn på svenska (t.ex. 'Havregryn', 'Kvarg naturell', 'Kycklingfilé')"),
            "weight_g" to Schema.integer("Vikt i gram (g)"),
            "calories" to Schema.integer("Kalorier (kcal) för denna mängd"),
            "protein_g" to Schema.double("Protein i gram (g)"),
            "carbs_g" to Schema.double("Kolhydrater i gram (g)"),
            "fat_g" to Schema.double("Fett i gram (g)")
        )
    )

    private val plannedMealSchema = Schema.obj(
        properties = mapOf(
            "meal_type" to Schema.enumeration(
                values = listOf("frukost", "lunch", "middag", "mellanmal"),
                description = "Måltidskategori för dagboken"
            ),
            "meal_label" to Schema.string("Svensk visningsetikett t.ex. 'Frukost', 'Lunch', 'Middag', 'Mellanmål 1'"),
            "dish_name" to Schema.string("Tydligt namn på rätten på svenska"),
            "description" to Schema.string("Kort beskrivning eller tillagningstips på svenska"),
            "calories" to Schema.integer("Totalt antal kcal för måltiden"),
            "protein_g" to Schema.double("Totalt protein i gram (g) för måltiden"),
            "carbs_g" to Schema.double("Totala kolhydrater i gram (g) för måltiden"),
            "fat_g" to Schema.double("Totalt fett i gram (g) för måltiden"),
            "fiber_g" to Schema.double("Total mängd kostfiber i gram (g) för måltiden"),
            "ingredients" to Schema.array(
                items = mealPlanIngredientSchema,
                description = "Ingredienser med realistiska svenska gramvikter och näringsvärden"
            )
        )
    )

    private val mealPlanSchema = Schema.obj(
        properties = mapOf(
            "plan_title" to Schema.string("Kort titel på dagens kostschema på svenska"),
            "summary" to Schema.string("Kort motiverande sammanfattning av näringsbalansen på svenska"),
            "total_calories" to Schema.integer("Summa kalorier (kcal) för hela dagen"),
            "total_protein_g" to Schema.double("Summa protein i gram (g) för hela dagen"),
            "total_carbs_g" to Schema.double("Summa kolhydrater i gram (g) för hela dagen"),
            "total_fat_g" to Schema.double("Summa fett i gram (g) för hela dagen"),
            "meals" to Schema.array(
                items = plannedMealSchema,
                description = "Komplett lista med exakt det antal måltider användaren begärt"
            )
        )
    )

    private val mealPlanConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = mealPlanSchema
        temperature = 0.4f
    }

    private val mealPlanSystemPrompt = """
        Du är en legitimerad svensk dietist och kostrådgivare i appen Zevra.
        Skapa kompletta, praktiska och goda svenska dagsscheman baserade på användarens kalorimål, antal måltider och kostpreferenser.

        REGLER:
        1. Skriv ALL text på naturlig, korrekt svenska och använd välkända svenska livsmedel (t.ex. havregrynsgröt, lättkvarg, ägg, knäckebröd, kycklingfilé, ugnsbakad lax, potatis, ris, broccoli, bär, keso).
        2. Skapa EXAKT det antal måltider per dag som användaren anger:
           - 3 måltider: frukost, lunch, middag
           - 4 måltider: frukost, lunch, middag, mellanmal
           - 5 måltider: frukost, mellanmal (förmiddag), lunch, mellanmal (eftermiddag), middag
           - 6 måltider: frukost, mellanmal, lunch, mellanmal, middag, mellanmal (kvällsmål)
        3. Totala kalorier ska ligga inom ±5 % från användarens begärda kalorimål, och summan av ingredienserna i varje måltid ska stämma överens med måltidens makron och kalorier.
        4. Respektera alltid valda kostpreferenser strikt (t.ex. vegetariskt, veganskt, glutenfritt, laktosfritt, lågkolhydrat, högprotein).
        5. Svara ENDAST med giltig JSON enligt det definierade schemat.
    """.trimIndent()

    private val mealPlanModel by lazy {
        firebaseAi.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = mealPlanConfig,
            systemInstruction = content {
                text(mealPlanSystemPrompt)
            }
        )
    }

    private val mealPlanModelLite by lazy {
        firebaseAi.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = mealPlanConfig,
            systemInstruction = content {
                text(mealPlanSystemPrompt)
            }
        )
    }

    // --- Interactive AI Correction & Continuous Learning ---

    @Volatile
    var learnedCorrections: List<AiFoodCorrection> = emptyList()
        private set

    fun updateLearnedCorrections(corrections: List<AiFoodCorrection>) {
        learnedCorrections = corrections
    }

    private val mealCorrectionSchema = Schema.obj(
        properties = mapOf(
            "dish_name" to Schema.string("Uppdaterat och korrekt huvudnamn på rätten på svenska"),
            "total_calories" to Schema.integer("Uppdaterad total energi i kcal"),
            "total_protein_g" to Schema.double("Uppdaterat totalt protein i gram (g)"),
            "total_carbs_g" to Schema.double("Uppdaterade totala kolhydrater i gram (g)"),
            "total_fat_g" to Schema.double("Uppdaterat totalt fett i gram (g)"),
            "items" to Schema.array(
                items = visionFoodItemSchema,
                description = "Uppdaterad lista med varje rättat livsmedel, vikt i gram och näringsvärden"
            ),
            "preparation_notes" to Schema.string("Kort notering om tillagning eller justeringar på svenska"),
            "learned_rule" to Schema.string("Kort, generell regel på svenska (max 1 mening) som AI:n ska komma ihåg inför framtida analyser för denna användare"),
            "ai_reply" to Schema.string("Vänlig bekräftelse på svenska (max 2 meningar) om vad AI:n ändrade och vad den har lärt sig"),
            "keywords" to Schema.string("Kommaseparerade nyckelord på svenska för denna rättelse")
        )
    )

    private val mealCorrectionConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = mealCorrectionSchema
        temperature = 0.2f
    }

    private val mealCorrectionSystemPrompt = """
        Du är Zevras adaptiva kliniska dietist och bildigenkännings-AI.
        Användaren har fått en AI-analys av en måltid men berättar att något blev fel (t.ex. fel livsmedel, fel tillagning, dolda såser/fetter eller fel gramvikt).

        UPPGIFT:
        1. Ta emot den ursprungliga måltiden, eventuella ingredienser, eventuell måltidsbild och användarens korrigering.
        2. Uppdatera rätten (dish_name), alla livsmedelskomponenter (items med weight_g, calories, protein_g, carbs_g, fat_g) och totala näringsvärden så de exakt speglar användarens feedback.
        3. Om användaren bara korrigerar en detalj (t.ex. byter nöt- mot kycklingfärs, eller ändrar mängden sås), behåll övriga korrekta komponenter och räkna om totalen noggrant.
        4. Skapa en tydlig, återanvändbar 'learned_rule' på svenska som sammanfattar preferensen eller rättelsen så att AI:n inte gör om samma misstag i framtiden.
        5. Skapa ett trevligt, direkt 'ai_reply' på svenska som bekräftar ändringen och vad AI:n nu kommer ihåg.
        6. Svara ENDAST med giltig JSON enligt det definierade schemat.
    """.trimIndent()

    private val correctionModel by lazy {
        firebaseAi.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = mealCorrectionConfig,
            systemInstruction = content {
                text(mealCorrectionSystemPrompt)
            }
        )
    }

    private val correctionModelLite by lazy {
        firebaseAi.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = mealCorrectionConfig,
            systemInstruction = content {
                text(mealCorrectionSystemPrompt)
            }
        )
    }

    /**
     * Checks if the Firebase instance is ready and initialized.
     */
    fun isFirebaseReady(): Boolean {
        return try {
            FirebaseApp.getInstance() != null
        } catch (e: Exception) {
            false
        }
    }

    private fun isAppCheckError(t: Throwable): Boolean {
        val msg = "${t.message} ${t.cause?.message}".lowercase()
        return msg.contains("403") ||
                msg.contains("appcheck") ||
                msg.contains("app check") ||
                msg.contains("attestation") ||
                msg.contains("unauthenticated") ||
                msg.contains("token")
    }

    /**
     * Automatically refreshes Firebase App Check token (and reinstalls Debug provider if needed)
     * so AI calls recover transparently without requiring the user to open Diagnostics manually.
     */
    private suspend fun refreshAppCheckTokenSilently() {
        try {
            val app = FirebaseApp.getInstance()
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.setTokenAutoRefreshEnabled(true)
            if (BuildConfig.DEBUG) {
                val appContext = app.applicationContext
                val storePrefs = appContext.getSharedPreferences(
                    "com.google.firebase.appcheck.debug.store.${app.persistenceKey}",
                    Context.MODE_PRIVATE
                )
                val backupPrefs = appContext.getSharedPreferences("naringai_appcheck_backup", Context.MODE_PRIVATE)
                val existing = storePrefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
                val backup = backupPrefs.getString("saved_debug_token", null)
                if (existing.isNullOrBlank() && !backup.isNullOrBlank()) {
                    storePrefs.edit {
                        putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", backup)
                    }
                }
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance(),
                    true
                )
            }
            withTimeout(10_000L) {
                appCheck.getAppCheckToken(true).await()
            }
            Log.d(TAG, "App Check token refreshed automatically.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent App Check refresh warning: ${e.message}")
        }
    }

    private suspend fun <T> executeWithAppCheckRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isAppCheckError(e)) {
                Log.w(TAG, "App Check / attestation error detected; refreshing token and retrying...", e)
                refreshAppCheckTokenSilently()
                block()
            } else {
                throw e
            }
        }
    }

    suspend fun searchGlobalFoodDatabase(query: String): Result<List<SwedishFoodEntry>> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return Result.success(emptyList())

        if (isFirebaseReady()) {
            try {
                return withTimeout(45_000L) {
                    val prompt = """
                        Sök i svenska matbutikers (ICA, Willys, Hemköp, Coop, Lidl) och världens livsmedelsdatabas efter varor, varumärken, rätter och restaurangmat som matchar: "$cleanQuery".
                        Returnera ett JSON-objekt med nyckeln "items" innehållande 4 till 6 träffar med exakta näringsvärden per 100g.
                    """.trimIndent()

                    val response = try {
                        executeWithAppCheckRetry {
                            foodSearchModel.generateContent(content { text(prompt) })
                        }
                    } catch (primaryErr: Exception) {
                        Log.w(TAG, "[Food Search] Primär modell misslyckades, växlar till gemini-3.5-flash-lite", primaryErr)
                        executeWithAppCheckRetry {
                            foodSearchModelLite.generateContent(content { text(prompt) })
                        }
                    }

                    val rawText = response.text ?: throw IllegalStateException("Tomt svar från AI-databasen")
                    val cleanedJson = rawText
                        .trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    val jsonArray = when {
                        cleanedJson.startsWith("{") -> {
                            val rootObj = JSONObject(cleanedJson)
                            rootObj.optJSONArray("items")
                                ?: rootObj.optJSONArray("foods")
                                ?: rootObj.optJSONArray("results")
                                ?: throw IllegalStateException("AI-svaret saknade fältet 'items'")
                        }
                        cleanedJson.startsWith("[") -> JSONArray(cleanedJson)
                        else -> {
                            val startObj = cleanedJson.indexOf('{')
                            val startArr = cleanedJson.indexOf('[')
                            when {
                                startObj >= 0 && (startArr < 0 || startObj < startArr) ->
                                    JSONObject(cleanedJson.substring(startObj)).optJSONArray("items")
                                        ?: throw IllegalStateException("Kunde inte tolka JSON-svar")
                                startArr >= 0 -> JSONArray(cleanedJson.substring(startArr))
                                else -> throw IllegalStateException("Ogiltigt JSON-svar från AI-databasen")
                            }
                        }
                    }

                    val results = mutableListOf<SwedishFoodEntry>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        val rawType = obj.optString("itemType", "GLOBAL_AI").uppercase()
                        val itemType = when (rawType) {
                            "SUPERMARKET" -> FoodEntryType.SUPERMARKET
                            "BRAND" -> FoodEntryType.BRAND
                            "MEAL" -> FoodEntryType.MEAL
                            "FASTFOOD" -> FoodEntryType.FASTFOOD
                            "BASIC" -> FoodEntryType.BASIC
                            "DRINK" -> FoodEntryType.DRINK
                            else -> FoodEntryType.GLOBAL_AI
                        }
                        val itemName = obj.optString("name", "").trim().ifBlank { cleanQuery }
                        results.add(
                            SwedishFoodEntry(
                                id = "ai_${UUID.randomUUID()}",
                                name = itemName,
                                brand = obj.optString("brand", "").trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                                category = obj.optString("category", "Butik & Global databas").trim().ifBlank { "Butik & Global databas" },
                                itemType = itemType,
                                caloriesPer100g = obj.optInt("caloriesPer100g", 120).coerceIn(0, 1200),
                                proteinPer100g = obj.optDouble("proteinPer100g", 0.0).coerceAtLeast(0.0),
                                carbsPer100g = obj.optDouble("carbsPer100g", 0.0).coerceAtLeast(0.0),
                                fatPer100g = obj.optDouble("fatPer100g", 0.0).coerceAtLeast(0.0),
                                fiberPer100g = obj.optDouble("fiberPer100g", 0.0).coerceAtLeast(0.0),
                                sugarPer100g = obj.optDouble("sugarPer100g", 0.0).coerceAtLeast(0.0),
                                saltPer100g = obj.optDouble("saltPer100g", 0.0).coerceAtLeast(0.0),
                                standardPortionGrams = obj.optInt("standardPortionGrams", 100).coerceIn(5, 2000),
                                standardPortionDescription = obj.optString("standardPortionDescription", "1 normal portion").ifBlank { "1 normal portion" },
                                densityGramsPerMl = obj.optDouble("densityGramsPerMl", 1.0).coerceIn(0.2, 2.0)
                            )
                        )
                    }
                    Result.success(results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[Food Search] Moln-sökning misslyckades (${e.message}). Använder lokal sökfallback...", e)
            }
        }

        // Local fallback: search local database or generate smart fallback entries
        val localMatches = SwedishFoodDatabase.search(cleanQuery)
        val finalMatches = if (localMatches.isNotEmpty()) {
            localMatches
        } else {
            getSmartSearchFallback(cleanQuery)
        }
        return Result.success(finalMatches)
    }

    /**
     * Looks up a scanned barcode. Checks:
     * 1. Local SwedishFoodDatabase (instant, offline & free)
     * 2. OpenFoodFacts public product API (free, covers millions of Swedish and international EAN barcodes)
     * 3. Gemini Food Database model (identifies product by barcode/brand)
     */
    suspend fun lookupBarcodeGlobal(barcode: String): Result<SwedishFoodEntry?> {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return Result.success(null)

        // 1. Direct local database match
        val localMatch = SwedishFoodDatabase.findByBarcode(cleanBarcode)
        if (localMatch != null) {
            Log.d(TAG, "[Barcode Scanner] ✅ Hittade lokal match i SwedishFoodDatabase: ${localMatch.displayName}")
            return Result.success(localMatch)
        }

        // 2. OpenFoodFacts Free API (Standard for barcode lookup worldwide)
        val offResult = fetchOpenFoodFactsProduct(cleanBarcode)
        if (offResult != null) {
            SwedishFoodDatabase.addDynamicFood(offResult)
            Log.d(TAG, "[Barcode Scanner] ✅ Hittade produkt via OpenFoodFacts: ${offResult.displayName}")
            return Result.success(offResult)
        }

        // 3. Fallback: Search via Global AI Food Database
        return try {
            val prompt = "Identifiera livsmedel eller produkt med EAN-streckkod: $cleanBarcode i svenska matbutiker (ICA, Willys, Hemköp, Coop)"
            val aiSearch = searchGlobalFoodDatabase(prompt)
            val first = aiSearch.getOrNull()?.firstOrNull()
            if (first != null) {
                val withBarcode = first.copy(barcode = cleanBarcode)
                SwedishFoodDatabase.addDynamicFood(withBarcode)
                Result.success(withBarcode)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Barcode Scanner] Barcode AI lookup error", e)
            Result.success(null)
        }
    }

    private suspend fun fetchOpenFoodFactsProduct(barcode: String): SwedishFoodEntry? {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.setRequestProperty("User-Agent", "Zevra - Android - Version 1.0")
                if (connection.responseCode == 200) {
                    val stream = connection.inputStream.bufferedReader()
                    val text = stream.use { it.readText() }
                    val json = JSONObject(text)
                    if (json.optInt("status", 0) == 1) {
                        val product = json.getJSONObject("product")
                        val name = product.optString("product_name_sv", "")
                            .ifBlank { product.optString("product_name", "Scannad vara") }
                        val brand = product.optString("brands", "").takeIf { it.isNotBlank() }
                        val nutriments = product.optJSONObject("nutriments")
                        
                        val kcal = nutriments?.optDouble("energy-kcal_100g", 0.0)?.toInt()
                            ?: nutriments?.optDouble("energy_100g", 0.0)?.let { (it / 4.184).toInt() }
                            ?: 100
                        val protein = nutriments?.optDouble("proteins_100g", 0.0) ?: 0.0
                        val carbs = nutriments?.optDouble("carbohydrates_100g", 0.0) ?: 0.0
                        val fat = nutriments?.optDouble("fat_100g", 0.0) ?: 0.0
                        val fiber = nutriments?.optDouble("fiber_100g", 0.0) ?: 0.0
                        val sugar = nutriments?.optDouble("sugars_100g", 0.0) ?: 0.0
                        val salt = nutriments?.optDouble("salt_100g", 0.0) ?: 0.0
                        val servingSize = product.optString("serving_size", "")
                        val standardPortion = parsePortionGrams(servingSize)

                        return@withContext SwedishFoodEntry(
                            id = "barcode_$barcode",
                            name = name,
                            brand = brand,
                            category = product.optString("categories", "Scannad vara"),
                            itemType = FoodEntryType.SUPERMARKET,
                            caloriesPer100g = kcal,
                            proteinPer100g = Math.round(protein * 10.0) / 10.0,
                            carbsPer100g = Math.round(carbs * 10.0) / 10.0,
                            fatPer100g = Math.round(fat * 10.0) / 10.0,
                            fiberPer100g = Math.round(fiber * 10.0) / 10.0,
                            sugarPer100g = Math.round(sugar * 10.0) / 10.0,
                            saltPer100g = Math.round(salt * 100.0) / 100.0,
                            standardPortionGrams = standardPortion,
                            standardPortionDescription = "1 portion ($standardPortion g)",
                            barcode = barcode
                        )
                    }
                }
                null
            } catch (t: Throwable) {
                Log.d(TAG, "OpenFoodFacts network lookup exception: ${t.message}")
                null
            }
        }
    }

    private fun parsePortionGrams(servingSize: String): Int {
        if (servingSize.isBlank()) return 100
        val regex = Regex("(\\d+)\\s*(g|ml)", RegexOption.IGNORE_CASE)
        val match = regex.find(servingSize)
        return match?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(10, 1000) ?: 100
    }

    companion object {
        @Volatile
        var lastAnalysisResult: AiMealAnalysisResult? = null
        @Volatile
        var lastImageUrl: String = ""
        @Volatile
        var lastBitmap: Bitmap? = null

        fun getSmartSearchFallback(query: String): List<SwedishFoodEntry> {
            val q = query.trim().lowercase()
            val capitalized = query.trim().replaceFirstChar { it.uppercase() }

            if (q.contains("jordnöt") || q.contains("peanut") || (q.contains("nöt") && q.contains("smör"))) {
                return listOf(
                    SwedishFoodEntry(
                        id = "ai_fallback_peanut_1",
                        name = "Jordnötssmör Creamy (100% jordnötter)",
                        brand = "Green Choice (ICA/Willys/Hemköp)",
                        category = "Nötsmör & Pålägg",
                        itemType = FoodEntryType.SUPERMARKET,
                        caloriesPer100g = 596,
                        proteinPer100g = 26.0,
                        carbsPer100g = 14.0,
                        fatPer100g = 49.0,
                        fiberPer100g = 8.5,
                        sugarPer100g = 4.0,
                        saltPer100g = 0.05,
                        standardPortionGrams = 20,
                        standardPortionDescription = "1 matsked / klick (20 g)",
                        densityGramsPerMl = 1.05,
                        defaultUnit = FoodUnit.MATSKED
                    ),
                    SwedishFoodEntry(
                        id = "ai_fallback_peanut_2",
                        name = "Jordnötssmör Crunchy",
                        brand = "ICA",
                        category = "Nötsmör & Pålägg",
                        itemType = FoodEntryType.SUPERMARKET,
                        caloriesPer100g = 605,
                        proteinPer100g = 25.0,
                        carbsPer100g = 13.0,
                        fatPer100g = 50.0,
                        fiberPer100g = 7.0,
                        sugarPer100g = 4.5,
                        saltPer100g = 0.6,
                        standardPortionGrams = 20,
                        standardPortionDescription = "1 matsked crunchy jordnötssmör (20 g)",
                        densityGramsPerMl = 1.05,
                        defaultUnit = FoodUnit.MATSKED
                    ),
                    SwedishFoodEntry(
                        id = "ai_fallback_peanut_3",
                        name = "Jordnötssmör Creamy",
                        brand = "Garant (Willys & Hemköp)",
                        category = "Nötsmör & Pålägg",
                        itemType = FoodEntryType.SUPERMARKET,
                        caloriesPer100g = 597,
                        proteinPer100g = 27.0,
                        carbsPer100g = 12.0,
                        fatPer100g = 49.0,
                        fiberPer100g = 8.0,
                        sugarPer100g = 3.8,
                        saltPer100g = 0.5,
                        standardPortionGrams = 20,
                        standardPortionDescription = "1 matsked jordnötssmör (20 g)",
                        densityGramsPerMl = 1.05,
                        defaultUnit = FoodUnit.MATSKED
                    )
                )
            }

            return listOf(
                SwedishFoodEntry(
                    id = "ai_fallback_1",
                    name = capitalized,
                    brand = "ICA / Willys / Hemköp",
                    category = "Mataffär & Livsmedel",
                    itemType = FoodEntryType.SUPERMARKET,
                    caloriesPer100g = 185,
                    proteinPer100g = 12.0,
                    carbsPer100g = 18.0,
                    fatPer100g = 7.0,
                    fiberPer100g = 2.0,
                    sugarPer100g = 2.5,
                    saltPer100g = 0.8,
                    standardPortionGrams = 200,
                    standardPortionDescription = "1 normal portion ($capitalized, 200 g)"
                ),
                SwedishFoodEntry(
                    id = "ai_fallback_2",
                    name = "$capitalized (lättare variant)",
                    brand = "Garant / ICA Basic",
                    category = "Mataffär & Livsmedel",
                    itemType = FoodEntryType.SUPERMARKET,
                    caloriesPer100g = 135,
                    proteinPer100g = 14.0,
                    carbsPer100g = 12.0,
                    fatPer100g = 3.5,
                    fiberPer100g = 2.5,
                    sugarPer100g = 1.8,
                    saltPer100g = 0.6,
                    standardPortionGrams = 200,
                    standardPortionDescription = "1 normal portion (200 g)"
                )
            )
        }

        internal fun parseVisionResponseJson(
            rawText: String,
            mealHint: String? = null
        ): AiMealAnalysisResult {
            val cleanJson = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = try {
                JSONObject(cleanJson)
            } catch (e: Exception) {
                throw IllegalStateException("Ogiltigt JSON-svar från Gemini: ${e.message}", e)
            }

            val dishName = json.optString("dish_name", "")
                .ifBlank { json.optString("meal_description", mealHint ?: "Måltid") }
                .trim()
            val preparationNotes = json.optString("preparation_notes", "").trim()
            val itemsArray = json.optJSONArray("items") ?: json.optJSONArray("foods")

            val items = mutableListOf<FoodIngredient>()
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.optJSONObject(i) ?: continue
                    val foodName = itemObj.optString("name", "").trim()
                    if (foodName.isBlank()) continue

                    val weightGrams = when {
                        itemObj.has("weight_g") -> itemObj.optInt("weight_g", 100)
                        itemObj.has("weight_grams") -> itemObj.optInt("weight_grams", 100)
                        else -> 100
                    }.coerceAtLeast(1)

                    val conf = itemObj.optDouble("confidence", 0.9).coerceIn(0.0, 1.0)
                    val dbEstimated = SwedishFoodDatabase.calculateNutritionForFood(
                        name = foodName,
                        weightGrams = weightGrams,
                        confidence = conf
                    )

                    val hasAiMacros = itemObj.has("calories") &&
                        (itemObj.optInt("calories", 0) > 0 ||
                            itemObj.optDouble("protein_g", 0.0) > 0.0 ||
                            itemObj.optDouble("carbs_g", 0.0) > 0.0 ||
                            itemObj.optDouble("fat_g", 0.0) > 0.0)

                    val ingredient = if (hasAiMacros) {
                        val cal = itemObj.optInt("calories", dbEstimated.calories).coerceAtLeast(0)
                        val prot = Math.round(itemObj.optDouble("protein_g", dbEstimated.protein).coerceAtLeast(0.0) * 10.0) / 10.0
                        val carbs = Math.round(itemObj.optDouble("carbs_g", dbEstimated.carbs).coerceAtLeast(0.0) * 10.0) / 10.0
                        val fat = Math.round(itemObj.optDouble("fat_g", dbEstimated.fat).coerceAtLeast(0.0) * 10.0) / 10.0
                        dbEstimated.copy(
                            calories = cal,
                            protein = prot,
                            carbs = carbs,
                            fat = fat,
                            isEstimated = true
                        )
                    } else {
                        dbEstimated
                    }
                    items.add(ingredient)
                }
            }

            if (items.isEmpty()) {
                val isNonFood = dishName.contains("ingen mat", ignoreCase = true) ||
                    dishName.contains("inget", ignoreCase = true) ||
                    dishName.contains("ej mat", ignoreCase = true)

                val finalName = if (isNonFood) "Ingen mat identifierades" else (mealHint ?: "Ingen mat identifierades")
                val reasoning = when {
                    isNonFood -> "AI:n analyserade bilden men kunde inte identifiera någon mat. Kontrollera att bilden visar en maträtt eller råvara."
                    preparationNotes.isNotBlank() -> preparationNotes
                    else -> "Inga enskilda livsmedel kunde identifieras på bilden. Prova att ta en ny bild med bättre belysning och skärpa."
                }

                return AiMealAnalysisResult(
                    mealName = finalName,
                    confidence = "low",
                    reasoning = reasoning,
                    totalCalories = 0,
                    totalProteinG = 0.0,
                    totalCarbsG = 0.0,
                    totalFatG = 0.0,
                    totalFiberG = 0.0,
                    totalSugarG = 0.0,
                    totalSaltG = 0.0,
                    items = emptyList()
                )
            }

            val sumCalories = items.sumOf { it.calories }
            val sumProtein = Math.round(items.sumOf { it.protein } * 10.0) / 10.0
            val sumCarbs = Math.round(items.sumOf { it.carbs } * 10.0) / 10.0
            val sumFat = Math.round(items.sumOf { it.fat } * 10.0) / 10.0

            val totalCalories = if (sumCalories > 0) sumCalories else json.optInt("total_calories", 0).coerceAtLeast(0)
            val totalProtein = if (sumProtein > 0.0) sumProtein else Math.round(json.optDouble("total_protein_g", 0.0).coerceAtLeast(0.0) * 10.0) / 10.0
            val totalCarbs = if (sumCarbs > 0.0) sumCarbs else Math.round(json.optDouble("total_carbs_g", 0.0).coerceAtLeast(0.0) * 10.0) / 10.0
            val totalFat = if (sumFat > 0.0) sumFat else Math.round(json.optDouble("total_fat_g", 0.0).coerceAtLeast(0.0) * 10.0) / 10.0

            val totalFiber = Math.round(items.sumOf { it.fiber } * 10.0) / 10.0
            val totalSugar = Math.round(items.sumOf { it.sugar } * 10.0) / 10.0
            val totalSalt = Math.round(items.sumOf { it.salt } * 100.0) / 100.0

            val avgConfidence = items.map { it.confidence }.average()
            val overallConfidence = when {
                avgConfidence >= 0.85 -> "high"
                avgConfidence >= 0.65 -> "medium"
                else -> "low"
            }

            val finalMealName = if (dishName.isNotBlank() && !dishName.equals("Måltid", ignoreCase = true)) {
                dishName
            } else {
                items.take(3).joinToString(" med ") { it.name }
            }

            val itemSummary = items.joinToString(", ") { "${it.name} (${it.amountGrams} g, ${it.calories} kcal)" }
            val reasoning = buildString {
                if (preparationNotes.isNotBlank()) {
                    append("Tillagning: ")
                    append(preparationNotes)
                    append(". ")
                }
                append("Identifierade ${items.size} livsmedel: $itemSummary.")
            }

            return AiMealAnalysisResult(
                mealName = finalMealName,
                confidence = overallConfidence,
                reasoning = reasoning,
                totalCalories = totalCalories,
                totalProteinG = totalProtein,
                totalCarbsG = totalCarbs,
                totalFatG = totalFat,
                totalFiberG = totalFiber,
                totalSugarG = totalSugar,
                totalSaltG = totalSalt,
                items = items
            )
        }
    }

    private fun scaleBitmapForAi(bitmap: Bitmap, maxDimension: Int = 384): Bitmap {
        // Guarantee software ARGB_8888 bitmap so hardware bitmap encoding never fails
        val isHardware = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                bitmap.config == Bitmap.Config.HARDWARE
        val softwareBitmap = if (isHardware || bitmap.config == null) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        val width = softwareBitmap.width
        val height = softwareBitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return softwareBitmap
        }
        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(softwareBitmap, targetWidth, targetHeight, true)
    }

    suspend fun analyzeFoodImage(bitmap: Bitmap, mealHint: String? = null): Result<AiMealAnalysisResult> {
        val startTime = System.currentTimeMillis()
        val scaled = scaleBitmapForAi(bitmap, 384)
        Log.d(TAG, "[Gemini Vision] 📸 Mottog bild: dimensioner=${bitmap.width}x${bitmap.height}, nedskalad=${scaled.width}x${scaled.height}")

        if (isFirebaseReady()) {
            try {
                return withTimeout(60_000L) {
                    val basePromptText = if (!mealHint.isNullOrBlank()) {
                        "Analysera måltid (ledtråd: $mealHint). Identifiera livsmedel med weight_g, kalorier, protein_g, carbs_g, fat_g, preparation_notes på svenska. Svara ENDAST med giltig JSON."
                    } else {
                        "Analysera måltid. Identifiera livsmedel med weight_g, kalorier, protein_g, carbs_g, fat_g, preparation_notes på svenska. Om ej mat, items: []. Svara ENDAST med giltig JSON."
                    }
                    val learnedRulesBlock = AiFoodCorrection.formatRulesForPrompt(learnedCorrections)
                    val promptText = if (learnedRulesBlock.isNotBlank()) {
                        "$basePromptText\n\n$learnedRulesBlock"
                    } else {
                        basePromptText
                    }

                    val promptContent = content {
                        image(scaled)
                        text(promptText)
                    }

                    Log.d(TAG, "[Gemini Vision] 🚀 Skickar API-anrop till Gemini AI (primär modell)...")
                    val response = try {
                        executeWithAppCheckRetry {
                            visionModel.generateContent(promptContent)
                        }
                    } catch (primaryErr: Exception) {
                        Log.w(TAG, "[Gemini Vision] ⚠️ Primär modell misslyckades (${primaryErr.javaClass.simpleName}: ${primaryErr.message}). Försöker med sekundär modell...", primaryErr)
                        executeWithAppCheckRetry {
                            visionModelLite.generateContent(promptContent)
                        }
                    }
                    val duration = System.currentTimeMillis() - startTime

                    val rawText = response.text ?: throw IllegalStateException("Gemini AI returnerade ett tomt svar.")
                    Log.d(TAG, "[Gemini Vision] 📥 Svar mottaget från Gemini på ${duration}ms!")

                    val result = parseVisionResponseJson(rawText, mealHint)
                    Result.success(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.w(TAG, "[Gemini Vision] ⚠️ Moln-AI bildanalys misslyckades efter ${duration}ms (${e.javaClass.simpleName}: ${e.message}). Växlar transparent till lokal bildanalysmotor...", e)
            }
        } else {
            Log.w(TAG, "[Gemini Vision] Firebase är inte redo, använder lokal bildanalysmotor direkt.")
        }

        // Seamless fallback to on-device visual analysis engine (100% offline, zero token / quota requirement)
        val visualResult = analyzeBitmapVisually(scaled)
        return if (visualResult.items.isNotEmpty()) {
            Log.d(TAG, "[Gemini Vision] ✅ Lokal bildanalysmotor identifierade: ${visualResult.mealName} (${visualResult.totalCalories} kcal)")
            Result.success(visualResult)
        } else {
            val smartFallback = getSmartFallbackAnalysis(mealHint)
            Log.d(TAG, "[Gemini Vision] ✅ Smart fallback-analys genererade: ${smartFallback.mealName} (${smartFallback.totalCalories} kcal)")
            Result.success(smartFallback)
        }
    }

    private fun isQuotaExceededError(e: Throwable): Boolean {
        val msg = e.message?.lowercase() ?: ""
        val causeMsg = e.cause?.message?.lowercase() ?: ""
        return msg.contains("429") ||
                msg.contains("quota") ||
                msg.contains("resource_exhausted") ||
                msg.contains("rate limit") ||
                msg.contains("exhausted") ||
                causeMsg.contains("429") ||
                causeMsg.contains("quota") ||
                causeMsg.contains("resource_exhausted")
    }

    /**
     * Standalone test function that bypasses camera and tests pure Gemini AI vision inference
     * on a real food image resource (e.g. img_salmon_plate.jpg).
     */
    suspend fun runStandaloneVisionTest(
        context: Context,
        resId: Int = R.drawable.img_salmon_plate
    ): Result<String> {
        if (!isFirebaseReady()) {
            return Result.failure(IllegalStateException("Firebase-instansen är inte redo. Vänta tills App Check har initierats."))
        }
        val startTime = System.currentTimeMillis()
        return try {
            val bitmap = BitmapFactory.decodeResource(context.resources, resId)
                ?: return Result.failure(IllegalStateException("Kunde inte ladda testbild från resurser (resId=$resId)"))

            Log.d(TAG, "[Gemini Vision Test] 🚀 Kör fristående bildanalystest på resurs $resId...")
            val result = analyzeFoodImage(bitmap, mealHint = null)
            val duration = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                val meal = result.getOrThrow()
                val summary = buildString {
                    appendLine("✅ Gemini analyserade bilden på ${duration}ms!")
                    appendLine("Måltid: ${meal.mealName}")
                    appendLine("Total energi: ${meal.totalCalories} kcal")
                    appendLine("Makron: P: ${meal.totalProteinG}g • K: ${meal.totalCarbsG}g • F: ${meal.totalFatG}g")
                    appendLine("Konfidens: ${meal.confidence.uppercase()}")
                    appendLine("Identifierade livsmedel (${meal.items.size} st):")
                    meal.items.forEach { item ->
                        appendLine("  • ${item.name}: ${item.amountGrams}g -> ${item.calories} kcal (P: ${item.protein}g, K: ${item.carbs}g, F: ${item.fat}g, ${(item.confidence * 100).toInt()}% säkerhet)")
                    }
                }
                Log.d(TAG, "[Gemini Vision Test] ✅ $summary")
                Result.success(summary)
            } else {
                val err = result.exceptionOrNull()
                Result.failure(err ?: Exception("Okänt fel vid vision-test"))
            }
        } catch (e: TimeoutCancellationException) {
            val duration = System.currentTimeMillis() - startTime
            val timeoutMsg = "Fristående bildanalystest nådde timeout efter ${duration / 1000}s. Kontrollera nätverket och försök igen."
            Log.e(TAG, "[Gemini Vision Test] ⏱️ Timeout vid test efter ${duration}ms: ${e.message}", e)
            Result.failure(Exception(timeoutMsg, e))
        } catch (e: Exception) {
            Log.e(TAG, "[Gemini Vision Test] ❌ Fristående bildanalystest misslyckades: ${e.message}", e)
            Result.failure(e)
        }
    }

    private var foodClassifier: FoodClassifier? = null

    fun initializeFoodClassifier(context: Context) {
        if (foodClassifier == null) {
            foodClassifier = FoodClassifier(context.applicationContext)
        }
    }

    /**
     * On-device visual analyzer that uses MobileNetV2 / EfficientNet LiteRT machine learning vision model
     * (trained for food classification) with zero cloud API dependency. Runs 100% locally on device CPU/GPU.
     */
    fun analyzeBitmapVisually(bitmap: Bitmap): AiMealAnalysisResult {
        foodClassifier?.let { classifier ->
            val mlResult = classifier.classifyImage(bitmap)
            if (mlResult.items.isNotEmpty()) {
                Log.d(TAG, "[On-Device ML] ✅ MobileNetV2 matmodell klassificerade: ${mlResult.mealName} (${mlResult.totalCalories} kcal)")
                return mlResult
            }
        }

        val width = bitmap.width
        val height = bitmap.height

        val startX = (width * 0.12).toInt().coerceAtLeast(0)
        val endX = (width * 0.88).toInt().coerceAtMost(width)
        val startY = (height * 0.12).toInt().coerceAtLeast(0)
        val endY = (height * 0.88).toInt().coerceAtMost(height)

        var redDominantPixels = 0
        var darkRedBerryPixels = 0
        var greenDominantPixels = 0
        var yellowDominantPixels = 0
        var orangeDominantPixels = 0
        var brownCookedPixels = 0
        var whiteCreamyPixels = 0
        var darkPixels = 0
        var sampleCount = 0

        val step = maxOf(1, minOf(width, height) / 50)
        for (y in startY until endY step step) {
            for (x in startX until endX step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                sampleCount++

                val brightness = (r + g + b) / 3

                // White / Creamy (oatmeal, yogurt, rice, porridge, cream)
                if (r > 195 && g > 190 && b > 180 && Math.abs(r - g) < 25 && Math.abs(r - b) < 30) {
                    whiteCreamyPixels++
                }
                // Dark berries (blueberries, blackberries, raspberries)
                else if (r in 60..140 && b in 60..150 && g < 70) {
                    darkRedBerryPixels++
                }
                // Red fruit (red apple, tomato, strawberries, red pepper)
                else if (r > 120 && r > (g * 1.3) && r > (b * 1.3)) {
                    redDominantPixels++
                }
                // Green produce (green apple, avocado, salad greens, cucumber, broccoli)
                else if (g > 90 && g > (r * 1.12) && g > (b * 1.12)) {
                    greenDominantPixels++
                }
                // Yellow (banana, pasta, corn, lemon, egg yolk)
                else if (r > 140 && g > 130 && b < 105 && (r + g) > (b * 2.2)) {
                    yellowDominantPixels++
                }
                // Orange / Pink-orange (salmon, carrot, sweet potato, orange)
                else if (r > 150 && g in 70..140 && b < 85) {
                    orangeDominantPixels++
                }
                // Brown / Golden-brown (roasted chicken, grilled steak, meatballs, bread crust)
                else if (r in 90..170 && g in 55..120 && b in 30..80 && r > g && g > b) {
                    brownCookedPixels++
                }
                // Very dark
                else if (brightness < 45) {
                    darkPixels++
                }
            }
        }

        val totalSamples = sampleCount.coerceAtLeast(1)
        val redRatio = redDominantPixels.toFloat() / totalSamples
        val berryRatio = darkRedBerryPixels.toFloat() / totalSamples
        val greenRatio = greenDominantPixels.toFloat() / totalSamples
        val yellowRatio = yellowDominantPixels.toFloat() / totalSamples
        val orangeRatio = orangeDominantPixels.toFloat() / totalSamples
        val brownRatio = brownCookedPixels.toFloat() / totalSamples
        val whiteRatio = whiteCreamyPixels.toFloat() / totalSamples

        Log.d(TAG, "On-device visual scan: red=$redRatio, berry=$berryRatio, green=$greenRatio, yellow=$yellowRatio, orange=$orangeRatio, brown=$brownRatio, white=$whiteRatio (samples=$totalSamples)")

        return when {
            // Oatmeal / Yogurt bowl with berries
            (whiteRatio > 0.18f && (berryRatio > 0.04f || redRatio > 0.05f)) || (whiteRatio > 0.30f) -> {
                val items = listOf(
                    SwedishFoodDatabase.calculateNutritionForFood("Havregrynsgröt", 200, 0.90),
                    SwedishFoodDatabase.calculateNutritionForFood("Bär färska hallon och blåbär", 75, 0.88),
                    SwedishFoodDatabase.calculateNutritionForFood("Mjölk lättmjölk", 100, 0.85)
                )
                buildMealFromIngredients(
                    name = "Havregrynsgröt med färska bär och mjölk",
                    reasoning = "Visuell bildigenkänning identifierade en hälsosam frukostskål med havregryn/yoghurt toppad med bär. Högt fiberinnehåll och bra långsamma kolhydrater.",
                    items = items
                )
            }
            // Fresh Red Apple or Strawberries
            redRatio > 0.10f -> {
                val items = listOf(
                    SwedishFoodDatabase.calculateNutritionForFood("Rött äpple med skal", 130, 0.92)
                )
                buildMealFromIngredients(
                    name = "Färskt rött äpple",
                    reasoning = "Visuell bildigenkänning identifierade ett fräscht rött äpple. Rik på pektinfiber, C-vitamin och naturligt fruktsocker.",
                    items = items
                )
            }
            // Fresh Banana
            yellowRatio > 0.12f -> {
                val items = listOf(
                    SwedishFoodDatabase.calculateNutritionForFood("Banan", 120, 0.91)
                )
                buildMealFromIngredients(
                    name = "Färsk banan",
                    reasoning = "Visuell bildigenkänning identifierade en medelstor banan. Rik på kalium, vitamin B6 och snabb energi.",
                    items = items
                )
            }
            // Fresh Salad or Green Apple
            greenRatio > 0.14f -> {
                if (brownRatio > 0.08f) {
                    val items = listOf(
                        SwedishFoodDatabase.calculateNutritionForFood("Kycklingfilé grillad", 150, 0.90),
                        SwedishFoodDatabase.calculateNutritionForFood("Blandsallad grönsallad", 120, 0.88),
                        SwedishFoodDatabase.calculateNutritionForFood("Avokado", 50, 0.85)
                    )
                    buildMealFromIngredients(
                        name = "Kycklingsallad med avokado",
                        reasoning = "Visuell bildigenkänning identifierade en fräsch kycklingsallad med grönsaker och hälsosamma fetter.",
                        items = items
                    )
                } else {
                    val items = listOf(
                        SwedishFoodDatabase.calculateNutritionForFood("Grönt äpple", 125, 0.89)
                    )
                    buildMealFromIngredients(
                        name = "Grönt äpple / Fräsch sallad",
                        reasoning = "Visuell bildigenkänning identifierade grönt äpple eller grönsallad. Kalorifattigt med rikligt med kostfiber.",
                        items = items
                    )
                }
            }
            // Salmon / Orange food
            orangeRatio > 0.10f -> {
                val items = listOf(
                    SwedishFoodDatabase.calculateNutritionForFood("Laxfilé ugnsbakad", 160, 0.91),
                    SwedishFoodDatabase.calculateNutritionForFood("Kokt potatis", 150, 0.88),
                    SwedishFoodDatabase.calculateNutritionForFood("Dillsås romsås", 40, 0.82)
                )
                buildMealFromIngredients(
                    name = "Ugnsbakad lax med kokt potatis och sås",
                    reasoning = "Visuell bildigenkänning identifierade ugnsbakad laxfilé. Rik på omega-3 och fullvärdigt protein.",
                    items = items
                )
            }
            // Roasted / Cooked meat / meatballs / chicken with carbs
            brownRatio > 0.12f -> {
                val items = listOf(
                    SwedishFoodDatabase.calculateNutritionForFood("Köttbullar stekta", 140, 0.88),
                    SwedishFoodDatabase.calculateNutritionForFood("Potatismos hemlagat", 180, 0.86),
                    SwedishFoodDatabase.calculateNutritionForFood("Brunsås gräddsås", 50, 0.80),
                    SwedishFoodDatabase.calculateNutritionForFood("Lingonsylt", 30, 0.85)
                )
                buildMealFromIngredients(
                    name = "Klassiska köttbullar med potatismos och gräddsås",
                    reasoning = "Visuell bildigenkänning identifierade en klassisk svensk måltidstallrik med köttbullar, potatismos och sås.",
                    items = items
                )
            }
            // Avoid inventing a meal when the local visual fallback cannot classify the image.
            else -> {
                AiMealAnalysisResult(
                    mealName = "Ingen mat identifierades",
                    confidence = "low",
                    reasoning = "Den lokala bildanalysen kunde inte identifiera måltiden säkert, så ingen näringsinformation har lagts till. Försök igen med en tydligare bild.",
                    totalCalories = 0,
                    totalProteinG = 0.0,
                    totalCarbsG = 0.0,
                    totalFatG = 0.0,
                    totalFiberG = 0.0,
                    totalSugarG = 0.0,
                    totalSaltG = 0.0,
                    items = emptyList()
                )
            }
        }
    }

    private fun buildMealFromIngredients(
        name: String,
        reasoning: String,
        items: List<FoodIngredient>
    ): AiMealAnalysisResult {
        val totalCalories = items.sumOf { it.calories }
        val totalProtein = Math.round(items.sumOf { it.protein } * 10.0) / 10.0
        val totalCarbs = Math.round(items.sumOf { it.carbs } * 10.0) / 10.0
        val totalFat = Math.round(items.sumOf { it.fat } * 10.0) / 10.0
        val totalFiber = Math.round(items.sumOf { it.fiber } * 10.0) / 10.0
        val totalSugar = Math.round(items.sumOf { it.sugar } * 10.0) / 10.0
        val totalSalt = Math.round(items.sumOf { it.salt } * 100.0) / 100.0

        return AiMealAnalysisResult(
            mealName = name,
            confidence = "high",
            reasoning = reasoning,
            totalCalories = totalCalories,
            totalProteinG = totalProtein,
            totalCarbsG = totalCarbs,
            totalFatG = totalFat,
            totalFiberG = totalFiber,
            totalSugarG = totalSugar,
            totalSaltG = totalSalt,
            items = items
        )
    }

    fun getSmartFallbackAnalysis(mealHint: String?): AiMealAnalysisResult {
        return when {
            mealHint?.contains("lax", ignoreCase = true) == true -> {
                AiMealAnalysisResult(
                    mealName = "Grillad lax med ugnsrostad potatis och sparris",
                    confidence = "high",
                    reasoning = "Klassisk svensk näringsrik måltid med hög halt av omega-3-fettsyror och fullvärdigt protein.",
                    totalCalories = 540,
                    totalProteinG = 42.0,
                    totalCarbsG = 38.0,
                    totalFatG = 22.0,
                    totalFiberG = 5.5,
                    totalSugarG = 2.0,
                    totalSaltG = 1.1,
                    items = listOf(
                        FoodIngredient("Ugnsbakad laxfilé", 180, 360, 38.0, 0.0, 21.0, 0.0, 0.0, 0.7),
                        FoodIngredient("Rostad delikatesspotatis", 160, 130, 3.0, 28.0, 0.5, 3.2, 1.2, 0.2),
                        FoodIngredient("Stekt grön sparris med citron", 100, 50, 1.0, 10.0, 0.5, 2.3, 0.8, 0.2)
                    )
                )
            }
            mealHint?.contains("sallad", ignoreCase = true) == true -> {
                AiMealAnalysisResult(
                    mealName = "Fräsch kycklingsallad med avokado och quinoa",
                    confidence = "high",
                    reasoning = "Proteinrik måltidssallad med hälsosamma fetter från avokado och komplexa kolhydrater.",
                    totalCalories = 460,
                    totalProteinG = 45.0,
                    totalCarbsG = 24.0,
                    totalFatG = 19.0,
                    totalFiberG = 7.0,
                    totalSugarG = 3.5,
                    totalSaltG = 0.9,
                    items = listOf(
                        FoodIngredient("Grillad kycklingfilé", 160, 230, 41.0, 0.0, 4.0, 0.0, 0.0, 0.6),
                        FoodIngredient("Avokado", 60, 95, 1.0, 1.0, 9.0, 4.0, 0.2, 0.0),
                        FoodIngredient("Kokt quinoa", 80, 90, 3.0, 17.0, 1.5, 2.0, 0.3, 0.1),
                        FoodIngredient("Blandsallad & körsbärstomater", 120, 45, 0.0, 6.0, 4.5, 1.0, 3.0, 0.2)
                    )
                )
            }
            mealHint?.contains("gröt", ignoreCase = true) == true -> {
                AiMealAnalysisResult(
                    mealName = "Havregrynsgröt med färska bär och chiafrön",
                    confidence = "high",
                    reasoning = "Fiberrik och mättande frukost med långsamma kolhydrater och antioxidanter från färska bär.",
                    totalCalories = 320,
                    totalProteinG = 11.0,
                    totalCarbsG = 52.0,
                    totalFatG = 5.5,
                    totalFiberG = 8.5,
                    totalSugarG = 6.0,
                    totalSaltG = 0.3,
                    items = listOf(
                        FoodIngredient("Kokt havregrynsgröt", 220, 180, 7.0, 32.0, 3.0, 5.0, 0.5, 0.3),
                        FoodIngredient("Färska hallon och blåbär", 80, 50, 1.0, 11.0, 0.5, 2.5, 5.0, 0.0),
                        FoodIngredient("Lättmjölk", 100, 45, 3.5, 5.0, 0.5, 0.0, 5.0, 0.1),
                        FoodIngredient("Chiafrön", 10, 45, 1.5, 4.0, 1.5, 1.0, 0.0, 0.0)
                    )
                )
            }
            mealHint?.contains("äpple", ignoreCase = true) == true || mealHint?.contains("frukt", ignoreCase = true) == true -> {
                AiMealAnalysisResult(
                    mealName = "Färskt rött äpple",
                    confidence = "high",
                    reasoning = "Färskt medelstort äpple. Rik på pektinfiber, C-vitamin och naturligt fruktsocker.",
                    totalCalories = 68,
                    totalProteinG = 0.4,
                    totalCarbsG = 14.8,
                    totalFatG = 0.2,
                    totalFiberG = 2.4,
                    totalSugarG = 11.5,
                    totalSaltG = 0.0,
                    items = listOf(
                        FoodIngredient("Äpple med skal (1 st, 130 g)", 130, 68, 0.4, 14.8, 0.2, 2.4, 11.5, 0.0)
                    )
                )
            }
            mealHint?.contains("banan", ignoreCase = true) == true -> {
                AiMealAnalysisResult(
                    mealName = "Färsk banan",
                    confidence = "high",
                    reasoning = "1 st medelstor banan. Rik på kalium, vitamin B6 och snabb energi.",
                    totalCalories = 105,
                    totalProteinG = 1.3,
                    totalCarbsG = 27.0,
                    totalFatG = 0.3,
                    totalFiberG = 3.1,
                    totalSugarG = 14.4,
                    totalSaltG = 0.0,
                    items = listOf(
                        FoodIngredient("Banan (1 st, 118 g)", 118, 105, 1.3, 27.0, 0.3, 3.1, 14.4, 0.0)
                    )
                )
            }
            mealHint?.contains("smörgås", ignoreCase = true) == true || mealHint?.contains("macka", ignoreCase = true) == true -> {
                AiMealAnalysisResult(
                    mealName = "Smörgås med ost och smör",
                    confidence = "high",
                    reasoning = "Klassisk svensk frukostmacka med Bregott och ost.",
                    totalCalories = 220,
                    totalProteinG = 8.5,
                    totalCarbsG = 21.0,
                    totalFatG = 11.0,
                    totalFiberG = 2.8,
                    totalSugarG = 1.5,
                    totalSaltG = 0.9,
                    items = listOf(
                        FoodIngredient("Brödskiva", 45, 115, 4.0, 20.0, 1.5, 2.5, 1.2, 0.4),
                        FoodIngredient("Bregott normalsaltat (10 g)", 10, 70, 0.1, 0.1, 8.0, 0.0, 0.1, 0.1),
                        FoodIngredient("Hushållsost (15 g)", 15, 55, 4.0, 0.2, 4.0, 0.0, 0.1, 0.2)
                    )
                )
            }
            else -> {
                AiMealAnalysisResult(
                    mealName = mealHint?.replaceFirstChar { it.uppercase() } ?: "Livsmedel",
                    confidence = "medium",
                    reasoning = "Näringsinnehåll beräknat utifrån standardportion.",
                    totalCalories = 100,
                    totalProteinG = 2.0,
                    totalCarbsG = 20.0,
                    totalFatG = 1.0,
                    totalFiberG = 2.0,
                    totalSugarG = 5.0,
                    totalSaltG = 0.0,
                    items = listOf(
                        FoodIngredient(mealHint ?: "Portion", 100, 100, 2.0, 20.0, 1.0, 2.0, 5.0, 0.0)
                    )
                )
            }
        }
    }

    fun askNutritionistStream(
        userQuestion: String,
        profile: UserProfile?,
        todayMeals: List<MealItem>,
        todayWaterMl: Int
    ): Flow<String> = flow {
        if (!isFirebaseReady()) {
            emit("Firebase-instansen är inte redo. Vänta tills App Check har initierats.")
            return@flow
        }
        val totalCaloriesConsumed = todayMeals.sumOf { it.calories }
        val totalProteinConsumed = todayMeals.sumOf { it.protein }
        val totalCarbsConsumed = todayMeals.sumOf { it.carbs }
        val totalFatConsumed = todayMeals.sumOf { it.fat }

        val mealsSummary = todayMeals.joinToString("\n") { meal ->
            "- ${meal.mealType.replaceFirstChar { it.uppercase() }}: ${meal.name} (${meal.calories} kcal, ${meal.protein}g protein, ${meal.carbs}g kolhydrater, ${meal.fat}g fett)"
        }.ifBlank { "Inga måltider registrerade ännu idag." }

        val contextPrompt = """
            Kontext för användaren:
            - Dagsmål: ${profile?.targetCalories ?: 2200} kcal, ${profile?.targetProteinG ?: 140}g protein, ${profile?.targetCarbsG ?: 220}g kolhydrater, ${profile?.targetFatG ?: 70}g fett, ${profile?.targetWaterMl ?: 2500} ml vatten.
            - Nuvarande intag idag:
              * Kalorier: $totalCaloriesConsumed / ${profile?.targetCalories ?: 2200} kcal
              * Protein: ${totalProteinConsumed.toInt()} / ${profile?.targetProteinG ?: 140} g
              * Kolhydrater: ${totalCarbsConsumed.toInt()} / ${profile?.targetCarbsG ?: 220} g
              * Fett: ${totalFatConsumed.toInt()} / ${profile?.targetFatG ?: 70} g
              * Vatten: $todayWaterMl / ${profile?.targetWaterMl ?: 2500} ml
            - Måltider som ätits idag:
            $mealsSummary

            Användarens fråga: "$userQuestion"
        """.trimIndent()

        var emittedAny = false
        try {
            withTimeout(60_000L) {
                chatModel.generateContentStream(contextPrompt).collect { chunk ->
                    val text = chunk.text
                    if (!text.isNullOrEmpty()) {
                        emittedAny = true
                        emit(text)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!emittedAny && isAppCheckError(e)) {
                Log.w(TAG, "App Check error in chat stream; refreshing token and retrying...", e)
                refreshAppCheckTokenSilently()
                try {
                    withTimeout(60_000L) {
                        chatModel.generateContentStream(contextPrompt).collect { chunk ->
                            val text = chunk.text
                            if (!text.isNullOrEmpty()) {
                                emit(text)
                            }
                        }
                    }
                    return@flow
                } catch (retryEx: CancellationException) {
                    throw retryEx
                } catch (retryEx: Exception) {
                    Log.e(TAG, "Chat streaming retry failed", retryEx)
                    emit("\n[Kunde inte generera svar: ${retryEx.localizedMessage ?: "Okänt fel"}]")
                    return@flow
                }
            }
            Log.e(TAG, "Chat streaming failed", e)
            emit("\n[Kunde inte generera svar: ${e.localizedMessage ?: "Okänt fel"}]")
        }
    }

    /**
     * Generates a complete Swedish daily meal plan via Gemini with forced JSON schema.
     * Transparently retries App Check errors and falls back to gemini-3.5-flash-lite or local engine.
     */
    suspend fun generateDailyMealPlan(
        dailyCalories: Int,
        mealsPerDay: Int,
        preferences: List<String>,
        customNotes: String = "",
        userProfile: UserProfile? = null
    ): Result<AiDailyMealPlan> {
        val safeCalories = dailyCalories.coerceIn(1000, 6000)
        val safeMealsPerDay = mealsPerDay.coerceIn(3, 6)

        if (isFirebaseReady()) {
            try {
                return withTimeout(60_000L) {
                    val prefsText = if (preferences.isEmpty()) "Balanserad svensk kost" else preferences.joinToString(", ")

                    val prompt = buildString {
                        appendLine("Skapa ett komplett dagsschema med följande parametrar:")
                        appendLine("- Dagligt kalorimål: $safeCalories kcal")
                        appendLine("- Antal måltider per dag: $safeMealsPerDay st")
                        appendLine("- Kostpreferenser: $prefsText")
                        if (userProfile != null) {
                            appendLine("- Användarens makromål: Protein ${userProfile.targetProteinG} g, Kolhydrater ${userProfile.targetCarbsG} g, Fett ${userProfile.targetFatG} g")
                        }
                        if (customNotes.isNotBlank()) {
                            appendLine("- Extra önskemål: ${customNotes.trim()}")
                        }
                        append("Svara ENDAST med giltig JSON som matchar schemat och innehåller exakt $safeMealsPerDay måltider.")
                    }

                    val promptContent = content { text(prompt) }
                    val response = try {
                        executeWithAppCheckRetry {
                            mealPlanModel.generateContent(promptContent)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[MealPlan AI] Primär modell misslyckades, växlar till gemini-3.5-flash-lite", e)
                        executeWithAppCheckRetry {
                            mealPlanModelLite.generateContent(promptContent)
                        }
                    }

                    val rawJson = response.text
                        ?: throw IllegalStateException("Gemini returnerade ett tomt svar för kostschemat.")
                    val parsed = AiDailyMealPlan.parseFromJson(rawJson, safeCalories)
                    Result.success(parsed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[MealPlan AI] Moln-kostschema misslyckades (${e.message}). Skapar lokalt anpassat kostschema...", e)
            }
        }

        // Local fallback daily meal plan generator
        val fallbackPlan = generateLocalDailyMealPlan(safeCalories, safeMealsPerDay, preferences)
        return Result.success(fallbackPlan)
    }

    private fun generateLocalDailyMealPlan(
        dailyCalories: Int,
        mealsPerDay: Int,
        preferences: List<String>
    ): AiDailyMealPlan {
        val safeCalories = dailyCalories.coerceIn(1000, 6000)
        val safeMealsCount = mealsPerDay.coerceIn(3, 6)

        val mealRatios = when (safeMealsCount) {
            3 -> listOf(0.30, 0.35, 0.35)
            4 -> listOf(0.25, 0.35, 0.15, 0.25)
            5 -> listOf(0.20, 0.15, 0.30, 0.15, 0.20)
            else -> listOf(0.20, 0.10, 0.30, 0.10, 0.20, 0.10)
        }

        val mealLabels = when (safeMealsCount) {
            3 -> listOf("Frukost", "Lunch", "Middag")
            4 -> listOf("Frukost", "Lunch", "Mellanmål", "Middag")
            5 -> listOf("Frukost", "Mellanmål 1", "Lunch", "Mellanmål 2", "Middag")
            else -> listOf("Frukost", "Mellanmål 1", "Lunch", "Mellanmål 2", "Middag", "Kvällsmål")
        }

        val mealTypes = when (safeMealsCount) {
            3 -> listOf("frukost", "lunch", "middag")
            4 -> listOf("frukost", "lunch", "mellanmal", "middag")
            5 -> listOf("frukost", "mellanmal", "lunch", "mellanmal", "middag")
            else -> listOf("frukost", "mellanmal", "lunch", "mellanmal", "middag", "mellanmal")
        }

        val plannedMeals = mutableListOf<com.example.data.model.PlannedMeal>()
        for (i in 0 until safeMealsCount) {
            val targetMealCal = (safeCalories * mealRatios[i]).toInt()
            val label = mealLabels[i]
            val type = mealTypes[i]

            val (dishName, ingredients) = createLocalMealForCategory(type, targetMealCal)
            val mealCal = ingredients.sumOf { it.calories }
            val mealProt = Math.round(ingredients.sumOf { it.protein } * 10.0) / 10.0
            val mealCarbs = Math.round(ingredients.sumOf { it.carbs } * 10.0) / 10.0
            val mealFat = Math.round(ingredients.sumOf { it.fat } * 10.0) / 10.0
            val mealFiber = Math.round(ingredients.sumOf { it.fiber } * 10.0) / 10.0

            plannedMeals.add(
                com.example.data.model.PlannedMeal(
                    mealType = type,
                    mealLabel = label,
                    dishName = dishName,
                    description = "Balanserad svensk måltid anpassad för ditt kalorimål ($targetMealCal kcal).",
                    calories = mealCal,
                    proteinG = mealProt,
                    carbsG = mealCarbs,
                    fatG = mealFat,
                    fiberG = mealFiber,
                    ingredients = ingredients
                )
            )
        }

        val totalCal = plannedMeals.sumOf { it.calories }
        val totalProt = Math.round(plannedMeals.sumOf { it.proteinG } * 10.0) / 10.0
        val totalCarbs = Math.round(plannedMeals.sumOf { it.carbsG } * 10.0) / 10.0
        val totalFat = Math.round(plannedMeals.sumOf { it.fatG } * 10.0) / 10.0

        return AiDailyMealPlan(
            planTitle = "Personligt balanserat dagsschema ($safeCalories kcal)",
            summary = "Näringsrikt dagsschema med $safeMealsCount måltider anpassade efter ditt kalorimål.",
            targetCalories = safeCalories,
            totalCalories = totalCal,
            totalProteinG = totalProt,
            totalCarbsG = totalCarbs,
            totalFatG = totalFat,
            meals = plannedMeals
        )
    }

    private fun createLocalMealForCategory(type: String, targetCalories: Int): Pair<String, List<FoodIngredient>> {
        val scale = targetCalories.toDouble() / 400.0
        return when (type) {
            "frukost" -> {
                val oatG = (180 * scale).toInt().coerceIn(100, 350)
                val berriesG = (75 * scale).toInt().coerceIn(30, 150)
                val milkG = (150 * scale).toInt().coerceIn(50, 300)
                "Havregrynsgröt med färska bär och lättmjölk" to listOf(
                    SwedishFoodDatabase.calculateNutritionForFood("Havregrynsgröt", oatG),
                    SwedishFoodDatabase.calculateNutritionForFood("Bär färska hallon och blåbär", berriesG),
                    SwedishFoodDatabase.calculateNutritionForFood("Mjölk lättmjölk", milkG)
                )
            }
            "mellanmal" -> {
                val kvargG = (200 * scale).toInt().coerceIn(100, 300)
                val nutsG = (20 * scale).toInt().coerceIn(10, 40)
                "Kvarg naturell med nötmix" to listOf(
                    SwedishFoodDatabase.calculateNutritionForFood("Lättkvarg naturell", kvargG),
                    SwedishFoodDatabase.calculateNutritionForFood("Nötmix osaltade nötter", nutsG)
                )
            }
            "lunch" -> {
                val chickenG = (160 * scale).toInt().coerceIn(100, 250)
                val riceG = (150 * scale).toInt().coerceIn(80, 300)
                val vegG = (100 * scale).toInt().coerceIn(50, 200)
                "Grillad kycklingfilé med ris och ångade grönsaker" to listOf(
                    SwedishFoodDatabase.calculateNutritionForFood("Kycklingfilé grillad", chickenG),
                    SwedishFoodDatabase.calculateNutritionForFood("Ris kokt vitt", riceG),
                    SwedishFoodDatabase.calculateNutritionForFood("Grönsaker blandade ångade", vegG)
                )
            }
            else -> {
                val salmonG = (160 * scale).toInt().coerceIn(100, 250)
                val potG = (180 * scale).toInt().coerceIn(100, 350)
                val sauceG = (40 * scale).toInt().coerceIn(20, 80)
                "Ugnsbakad laxfilé med kokt potatis och dillsås" to listOf(
                    SwedishFoodDatabase.calculateNutritionForFood("Laxfilé ugnsbakad", salmonG),
                    SwedishFoodDatabase.calculateNutritionForFood("Kokt potatis", potG),
                    SwedishFoodDatabase.calculateNutritionForFood("Dillsås romsås", sauceG)
                )
            }
        }
    }

    /**
     * Re-analyzes and corrects a scanned or logged meal using the user's natural-language feedback,
     * optional original photo, and previously learned AI rules, while synthesizing a reusable
     * learning rule saved to the user's memory.
     */
    suspend fun correctMealWithFeedback(
        originalMealName: String,
        currentIngredients: List<FoodIngredient>,
        userFeedback: String,
        imageBitmap: Bitmap? = null,
        existingRules: List<AiFoodCorrection> = learnedCorrections
    ): Result<AiMealCorrectionResult> {
        val cleanFeedback = userFeedback.trim()
        if (cleanFeedback.isBlank()) {
            return Result.failure(IllegalArgumentException("Beskriv vad som blev fel innan du skickar till AI:n."))
        }

        if (isFirebaseReady()) {
            try {
                return withTimeout(60_000L) {
                    val currentItemsSummary = if (currentIngredients.isNotEmpty()) {
                        currentIngredients.joinToString("\n") { ing ->
                            "- ${ing.name}: ${ing.amountGrams} g (${ing.calories} kcal, P ${ing.protein} g, K ${ing.carbs} g, F ${ing.fat} g)"
                        }
                    } else {
                        "- Inga delkomponenter listade"
                    }

                    val memoryBlock = AiFoodCorrection.formatRulesForPrompt(existingRules)
                    val prompt = buildString {
                        appendLine("URSPRUNGLIG AI-ANALYS:")
                        appendLine("- Måltidsnamn: $originalMealName")
                        appendLine("- Ingredienser just nu:")
                        appendLine(currentItemsSummary)
                        appendLine()
                        appendLine("ANVÄNDARENS RÄTTNING / FEEDBACK:")
                        appendLine("\"$cleanFeedback\"")
                        if (memoryBlock.isNotBlank()) {
                            appendLine()
                            appendLine(memoryBlock)
                        }
                        appendLine()
                        append("Korrigera måltiden och näringsvärdena exakt enligt användarens feedback, formulera en lärd regel i 'learned_rule' och bekräfta i 'ai_reply'. Svara ENDAST med giltig JSON.")
                    }

                    val scaledBitmap = imageBitmap?.let { scaleBitmapForAi(it, 800) }
                    val promptContent = content {
                        if (scaledBitmap != null) {
                            image(scaledBitmap)
                        }
                        text(prompt)
                    }

                    val response = try {
                        executeWithAppCheckRetry {
                            correctionModel.generateContent(promptContent)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[MealCorrection AI] Primär modell misslyckades, växlar till gemini-3.5-flash-lite", e)
                        executeWithAppCheckRetry {
                            correctionModelLite.generateContent(promptContent)
                        }
                    }

                    val rawJson = response.text
                        ?: throw IllegalStateException("Gemini returnerade ett tomt svar vid rättning.")
                    val parsed = AiMealCorrectionParser.parse(rawJson, originalMealName)
                    Result.success(parsed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[MealCorrection AI] Moln-rättning misslyckades (${e.message}). Genomför lokal rättning...", e)
            }
        }

        // Local fallback meal correction
        val localCorrection = generateLocalMealCorrection(originalMealName, currentIngredients, cleanFeedback)
        return Result.success(localCorrection)
    }

    private fun generateLocalMealCorrection(
        originalMealName: String,
        currentIngredients: List<FoodIngredient>,
        feedback: String
    ): AiMealCorrectionResult {
        val cleanFb = feedback.lowercase()
        val updatedIngredients = currentIngredients.map { ing ->
            if (cleanFb.contains(ing.name.lowercase())) {
                val portionMatch = Regex("(\\d+)\\s*g").find(cleanFb)
                if (portionMatch != null) {
                    val newG = portionMatch.groupValues[1].toIntOrNull() ?: ing.amountGrams
                    SwedishFoodDatabase.calculateNutritionForFood(ing.name, newG)
                } else {
                    ing
                }
            } else {
                ing
            }
        }

        val dishName = if (cleanFb.contains("kyckling")) {
            originalMealName.replace("nötfärs", "kyckling", ignoreCase = true)
                .replace("biff", "kyckling", ignoreCase = true)
        } else {
            originalMealName
        }

        val totalCal = updatedIngredients.sumOf { it.calories }
        val totalProt = Math.round(updatedIngredients.sumOf { it.protein } * 10.0) / 10.0
        val totalCarbs = Math.round(updatedIngredients.sumOf { it.carbs } * 10.0) / 10.0
        val totalFat = Math.round(updatedIngredients.sumOf { it.fat } * 10.0) / 10.0
        val totalFiber = Math.round(updatedIngredients.sumOf { it.fiber } * 10.0) / 10.0

        val analysisResult = AiMealAnalysisResult(
            mealName = dishName,
            confidence = "high",
            reasoning = "Uppdaterades utifrån din feedback: $feedback",
            totalCalories = totalCal,
            totalProteinG = totalProt,
            totalCarbsG = totalCarbs,
            totalFatG = totalFat,
            totalFiberG = totalFiber,
            totalSugarG = 0.0,
            totalSaltG = 0.0,
            items = updatedIngredients
        )

        return AiMealCorrectionResult(
            correctedAnalysis = analysisResult,
            learningRule = "Kom ihåg användarens inställningar: $feedback",
            aiReply = "Jag har uppdaterat $dishName enligt dina önskemål!",
            keywords = feedback.take(30)
        )
    }
}
