package com.example.data.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.example.data.repository.AiNutritionService
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

private const val TAG = "AiDiagnosticsService"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

data class AppCheckDiagnostics(
    val isInitialized: Boolean,
    val projectId: String,
    val buildVariant: String,
    val hasDebugSecretStored: Boolean,
    val debugSecretDisplay: String?,
    val standardTokenSuccess: Boolean,
    val standardTokenDetails: String?,
    val limitedUseTokenSuccess: Boolean,
    val limitedUseTokenDetails: String?,
    val errorClass: String? = null,
    val errorMessage: String? = null,
    val httpStatusCode: Int? = null,
    val diagnosis: String
)

data class VisionTestResult(
    val testName: String,
    val isSuccess: Boolean,
    val durationMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageBytes: Int,
    val imageConfig: String,
    val inlineBytesVerified: Boolean,
    val modelName: String,
    val rawResponse: String?,
    val parsedMealName: String?,
    val parsedCalories: Int?,
    val parsedIngredientsCount: Int?,
    val errorClass: String? = null,
    val errorMessage: String? = null,
    val httpStatusCode: Int? = null,
    val diagnosis: String
)

class AiDiagnosticsService(
    private val aiNutritionService: AiNutritionService
) {

    /**
     * Extracts an HTTP status code (e.g. 400, 403, 404, 429, 500) from an exception or message.
     */
    private fun extractHttpStatusCode(e: Throwable): Int? {
        val fullText = "${e.javaClass.name}: ${e.message} ${e.cause?.message}"
        val pattern = Pattern.compile("\\b([45][0-9]{2})\\b")
        val matcher = pattern.matcher(fullText)
        return if (matcher.find()) {
            matcher.group(1)?.toIntOrNull()
        } else {
            null
        }
    }

    /**
     * Checks if Firebase App Check actually succeeds against Google's servers.
     */
    suspend fun verifyAppCheck(context: Context): AppCheckDiagnostics {
        val isInitialized = FirebaseApp.getApps(context).isNotEmpty()
        if (!isInitialized) {
            return AppCheckDiagnostics(
                isInitialized = false,
                projectId = "Ingen",
                buildVariant = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE",
                hasDebugSecretStored = false,
                debugSecretDisplay = null,
                standardTokenSuccess = false,
                standardTokenDetails = null,
                limitedUseTokenSuccess = false,
                limitedUseTokenDetails = null,
                errorMessage = "FirebaseApp är inte initialiserad.",
                httpStatusCode = null,
                diagnosis = "CRITICAL: FirebaseApp.initializeApp() har inte körts."
            )
        }

        val app = FirebaseApp.getInstance()
        val projectId = app.options.projectId ?: "Okänt"
        val persistenceKey = app.persistenceKey
        val prefs = context.getSharedPreferences("$DEBUG_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
        val storedSecret = prefs.getString(DEBUG_SECRET_KEY, null)
        val hasSecret = !storedSecret.isNullOrBlank()

        val secretStatusText = if (hasSecret) "Konfigurerad (dold av säkerhetsskäl)" else "(Ingen token sparad)"

        var standardSuccess = false
        var standardDetails: String? = null
        var limitedSuccess = false
        var limitedDetails: String? = null
        var errorClass: String? = null
        var errorMessage: String? = null
        var httpCode: Int? = null

        val appCheck = FirebaseAppCheck.getInstance()

        // 1. Test Standard App Check Token
        try {
            val tokenResult = withTimeout(12_000L) {
                appCheck.getAppCheckToken(true).await()
            }
            standardSuccess = true
            val expiryDate = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(tokenResult.expireTimeMillis))
            standardDetails = "Verifierad (löper ut: $expiryDate)"
        } catch (t: Throwable) {
            standardSuccess = false
            errorClass = t.javaClass.simpleName
            errorMessage = t.localizedMessage ?: t.message ?: "Kunde inte hämta App Check-status"
            httpCode = extractHttpStatusCode(t)
            standardDetails = "Misslyckades: $errorMessage"
            Log.e(TAG, "[App Check Diag] getAppCheckToken misslyckades", t)
        }

        // 2. Test Limited-Use Token (Required by Firebase AI Logic)
        try {
            withTimeout(12_000L) {
                appCheck.getLimitedUseAppCheckToken().await()
            }
            limitedSuccess = true
            limitedDetails = "Verifierad för AI-anrop"
        } catch (t: Throwable) {
            limitedSuccess = false
            if (errorClass == null) {
                errorClass = t.javaClass.simpleName
                errorMessage = t.localizedMessage ?: t.message
                httpCode = extractHttpStatusCode(t)
            }
            limitedDetails = "Misslyckades: ${t.localizedMessage ?: t.message}"
            Log.e(TAG, "[App Check Diag] getLimitedUseAppCheckToken misslyckades", t)
        }

        val diagnosis = when {
            standardSuccess && limitedSuccess -> {
                "✅ App Check fungerar 100%! Verifieringen godkändes av Firebase backend."
            }
            standardSuccess && !limitedSuccess -> {
                "⚠️ Standardverifiering godkändes, men AI-attesteringen misslyckades."
            }
            httpCode == 403 -> {
                if (BuildConfig.DEBUG) {
                    "❌ HTTP 403 Forbidden: Firebase backend avvisade debug-verifiering. Kontrollera att debug-nyckeln är registrerad i Firebase Console."
                } else {
                    "❌ HTTP 403 Forbidden: Play Integrity attestering avvisades av Firebase backend."
                }
            }
            !hasSecret && BuildConfig.DEBUG -> {
                "⚠️ Ingen debug-konfiguration är lagrad i SharedPreferences."
            }
            else -> {
                "❌ App Check-verifiering misslyckades (${errorClass ?: "Fel"}): ${errorMessage ?: "Okänt nätverksfel"}"
            }
        }

        return AppCheckDiagnostics(
            isInitialized = true,
            projectId = projectId,
            buildVariant = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE",
            hasDebugSecretStored = hasSecret,
            debugSecretDisplay = secretStatusText,
            standardTokenSuccess = standardSuccess,
            standardTokenDetails = standardDetails,
            limitedUseTokenSuccess = limitedSuccess,
            limitedUseTokenDetails = limitedDetails,
            errorClass = errorClass,
            errorMessage = errorMessage,
            httpStatusCode = httpCode,
            diagnosis = diagnosis
        )
    }

    /**
     * Executes standalone vision test on a real drawable resource without camera dependency.
     */
    suspend fun runStandaloneResourceTest(
        context: Context,
        resId: Int = R.drawable.img_salmon_plate,
        resourceName: String = "Lax & potatis (resurs)"
    ): VisionTestResult {
        val startTime = System.currentTimeMillis()
        val bitmap = BitmapFactory.decodeResource(context.resources, resId)
            ?: return VisionTestResult(
                testName = resourceName,
                isSuccess = false,
                durationMs = 0,
                imageWidth = 0,
                imageHeight = 0,
                imageBytes = 0,
                imageConfig = "NONE",
                inlineBytesVerified = false,
                modelName = "${AiNutritionService.PRIMARY_MODEL_NAME} (fallback: ${AiNutritionService.FALLBACK_MODEL_NAME})",
                rawResponse = null,
                parsedMealName = null,
                parsedCalories = null,
                parsedIngredientsCount = null,
                errorMessage = "Resursbilden (id=$resId) kunde inte avkodas till Bitmap.",
                diagnosis = "Resursfel: Kontrollera att bildfilen finns i drawable."
            )

        return testBitmapAnalysis(
            testName = resourceName,
            bitmap = bitmap,
            startTime = startTime
        )
    }

    /**
     * Tests image analysis on an actual bitmap captured by camera or loaded from gallery.
     */
    suspend fun runCameraBitmapTest(
        bitmap: Bitmap,
        label: String = "Kamerabild"
    ): VisionTestResult {
        val startTime = System.currentTimeMillis()
        return testBitmapAnalysis(
            testName = label,
            bitmap = bitmap,
            startTime = startTime
        )
    }

    private suspend fun testBitmapAnalysis(
        testName: String,
        bitmap: Bitmap,
        startTime: Long
    ): VisionTestResult {
        val width = bitmap.width
        val height = bitmap.height
        val byteCount = bitmap.byteCount
        val configStr = bitmap.config?.name ?: "UNKNOWN"

        // Check JPEG compression simulation to verify real bytes can be sent
        val bos = ByteArrayOutputStream()
        val compressSuccess = bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        val jpegBytes = bos.toByteArray()
        val inlineVerified = compressSuccess && jpegBytes.isNotEmpty()

        Log.d(TAG, "[Diagnostics] $testName: ${width}x${height}, $byteCount bytes, config=$configStr, compressed JPEG size=${jpegBytes.size} bytes")

        val result = aiNutritionService.analyzeFoodImage(bitmap, mealHint = null)
        val durationMs = System.currentTimeMillis() - startTime

        return if (result.isSuccess) {
            val analysis = result.getOrThrow()
            VisionTestResult(
                testName = testName,
                isSuccess = true,
                durationMs = durationMs,
                imageWidth = width,
                imageHeight = height,
                imageBytes = byteCount,
                imageConfig = configStr,
                inlineBytesVerified = inlineVerified,
                modelName = "${AiNutritionService.PRIMARY_MODEL_NAME} (fallback: ${AiNutritionService.FALLBACK_MODEL_NAME})",
                rawResponse = analysis.reasoning,
                parsedMealName = analysis.mealName,
                parsedCalories = analysis.totalCalories,
                parsedIngredientsCount = analysis.items.size,
                diagnosis = "✅ Gemini analyserade bilden på ${durationMs}ms: Identifierade '${analysis.mealName}' (${analysis.totalCalories} kcal, ${analysis.items.size} ingredienser)!"
            )
        } else {
            val exception = result.exceptionOrNull() ?: Exception("Okänt AI-fel")
            val httpCode = extractHttpStatusCode(exception)
            val errorClass = exception.javaClass.simpleName
            val errorMsg = exception.localizedMessage ?: exception.message ?: "AI-anrop misslyckades"

            val diagnosis = when {
                httpCode == 403 || errorMsg.contains("App Check", ignoreCase = true) -> {
                    "❌ HTTP 403 / App Check fel: Gemini nekade förfrågan p.g.a. att App Check-attestering misslyckades."
                }
                httpCode == 429 || errorMsg.contains("quota", ignoreCase = true) -> {
                    "⚠️ HTTP 429 Rate Limit: Kvoten för Gemini API är tillfälligt nådd. Vänta några sekunder."
                }
                errorMsg.contains("timeout", ignoreCase = true) -> {
                    "⏱️ Timeout: Gemini svarade inte inom 60 sekunder. Kontrollera nätverksanslutningen."
                }
                else -> {
                    "❌ Gemini-analys misslyckades ($errorClass): $errorMsg"
                }
            }

            VisionTestResult(
                testName = testName,
                isSuccess = false,
                durationMs = durationMs,
                imageWidth = width,
                imageHeight = height,
                imageBytes = byteCount,
                imageConfig = configStr,
                inlineBytesVerified = inlineVerified,
                modelName = "${AiNutritionService.PRIMARY_MODEL_NAME} (fallback: ${AiNutritionService.FALLBACK_MODEL_NAME})",
                rawResponse = null,
                parsedMealName = null,
                parsedCalories = null,
                parsedIngredientsCount = null,
                errorClass = errorClass,
                errorMessage = errorMsg,
                httpStatusCode = httpCode,
                diagnosis = diagnosis
            )
        }
    }

    /**
     * Updates or registers a custom debug token in SharedPreferences so DebugAppCheckProviderFactory
     * uses the allow-listed token configured in the Firebase Console.
     */
    fun saveCustomDebugToken(context: Context, token: String): Boolean {
        return try {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) return false
            val app = FirebaseApp.getInstance()
            val persistenceKey = app.persistenceKey
            val prefs = context.getSharedPreferences("$DEBUG_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
            val backupPrefs = context.getSharedPreferences("naringai_appcheck_backup", Context.MODE_PRIVATE)
            val success = prefs.edit().putString(DEBUG_SECRET_KEY, cleanToken).commit()
            backupPrefs.edit().putString("saved_debug_token", cleanToken).apply()
            if (success) {
                val appCheck = FirebaseAppCheck.getInstance()
                appCheck.setTokenAutoRefreshEnabled(true)
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance(),
                    true
                )
                Log.d(TAG, "Custom debug token saved and DebugAppCheckProviderFactory reinstalled.")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom debug token", e)
            false
        }
    }

    /**
     * Reads current debug token stored in SharedPreferences.
     */
    fun getStoredDebugToken(context: Context): String? {
        return try {
            val app = FirebaseApp.getInstance()
            val persistenceKey = app.persistenceKey
            val prefs = context.getSharedPreferences("$DEBUG_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
            val stored = prefs.getString(DEBUG_SECRET_KEY, null)
            if (!stored.isNullOrBlank()) return stored
            val backupPrefs = context.getSharedPreferences("naringai_appcheck_backup", Context.MODE_PRIVATE)
            backupPrefs.getString("saved_debug_token", null)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Tests image analysis on a synthetic non-food bitmap (e.g. geometric pattern),
     * verifying that Gemini and the pipeline return 'Ingen mat identifierades' without crashing.
     */
    suspend fun runNonFoodTest(): VisionTestResult {
        val startTime = System.currentTimeMillis()
        val nonFoodBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(nonFoodBitmap)
        canvas.drawColor(android.graphics.Color.LTGRAY)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            strokeWidth = 6f
        }
        canvas.drawLine(0f, 0f, 400f, 400f, paint)
        canvas.drawLine(0f, 400f, 400f, 0f, paint)

        return testBitmapAnalysis(
            testName = "Icke-mat (geometriskt testmönster)",
            bitmap = nonFoodBitmap,
            startTime = startTime
        )
    }

    /**
     * Diagnostic test for JSON validation and edge cases.
     */
    fun testJsonAndEdgeCases(): String {
        val results = StringBuilder()

        // Test 1: Valid multi-food JSON using the clinical nutritionist schema
        val validJson = """
            {
              "dish_name": "Grillad kycklingfilé med ris och broccoli",
              "total_calories": 530,
              "total_protein_g": 45.2,
              "total_carbs_g": 54.0,
              "total_fat_g": 12.4,
              "items": [
                { "name": "Kycklingfilé", "weight_g": 150, "calories": 240, "protein_g": 38.0, "carbs_g": 0.0, "fat_g": 8.5 },
                { "name": "Kokt ris", "weight_g": 180, "calories": 234, "protein_g": 4.9, "carbs_g": 50.4, "fat_g": 0.7 },
                { "name": "Broccoli", "weight_g": 80, "calories": 28, "protein_g": 2.8, "carbs_g": 2.5, "fat_g": 0.3 },
                { "name": "Stekfett (rapsolja)", "weight_g": 3, "calories": 28, "protein_g": 0.0, "carbs_g": 0.0, "fat_g": 3.0 }
              ],
              "preparation_notes": "Kycklingfilé stekt i rapsolja, serverad med kokt jasminris och ångad broccoli"
            }
        """.trimIndent()
        try {
            val parsed = com.example.data.repository.AiNutritionService.parseVisionResponseJson(validJson)
            results.appendLine("✅ Test 1 (Giltig multi-food JSON): '${parsed.mealName}' (${parsed.items.size} livsmedel, ${parsed.totalCalories} kcal).")
        } catch (e: Exception) {
            results.appendLine("❌ Test 1 misslyckades: ${e.message}")
        }

        // Test 2: Non-food JSON (empty items array)
        val nonFoodJson = """
            {
              "dish_name": "Ingen mat identifierades",
              "total_calories": 0,
              "total_protein_g": 0.0,
              "total_carbs_g": 0.0,
              "total_fat_g": 0.0,
              "items": [],
              "preparation_notes": ""
            }
        """.trimIndent()
        try {
            val parsedEmpty = com.example.data.repository.AiNutritionService.parseVisionResponseJson(nonFoodJson)
            if (parsedEmpty.items.isEmpty() && parsedEmpty.totalCalories == 0) {
                results.appendLine("✅ Test 2 (Icke-mat JSON): Tom livsmedelslista hanterad korrekt med '${parsedEmpty.mealName}'.")
            } else {
                results.appendLine("❌ Test 2 misslyckades: Förväntade tom lista.")
            }
        } catch (e: Exception) {
            results.appendLine("❌ Test 2 misslyckades: ${e.message}")
        }

        // Test 3: Malformed JSON handling
        val malformedJson = "{ items: [ invalid json ... "
        try {
            com.example.data.repository.AiNutritionService.parseVisionResponseJson(malformedJson)
            results.appendLine("❌ Test 3 misslyckades: Ogiltig JSON borde ha kastat fel.")
        } catch (e: Exception) {
            results.appendLine("✅ Test 3 (Ogiltig JSON): Fångades säkert av parsern utan krasch (${e.javaClass.simpleName}).")
        }

        return results.toString()
    }
}
