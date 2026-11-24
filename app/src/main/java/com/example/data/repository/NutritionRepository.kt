package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.AiFoodCorrection
import com.example.data.model.MealItem
import com.example.data.model.Recipe
import com.example.data.model.UserProfile
import com.example.data.model.WaterLog
import com.example.data.model.WorkoutLog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "NutritionRepository"

class NutritionRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun currentUidFlow(): Flow<String> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid ?: "unauthenticated")
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid ?: "unauthenticated")
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Användaren måste vara inloggad.")
    }

    // --- User Profile ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUserProfile(): Flow<UserProfile?> = currentUidFlow().flatMapLatest { uid ->
        db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java)?.copy(id = snapshot.id)
                } else {
                    null
                }
            }
    }.catch { e ->
        Log.e(TAG, "Error observing user profile", e)
        throw e
    }

    suspend fun getUserProfile(): Result<UserProfile?> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.success(null)
            val doc = db.collection("users").document(uid).get().await()
            val profile = if (doc.exists()) doc.toObject(UserProfile::class.java)?.copy(id = doc.id) else null
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile", e)
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val payload = profile.toMap().toMutableMap()
            payload["userId"] = uid
            if (!snapshot.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            } else {
                val existingCreatedAt = snapshot.getTimestamp("createdAt")
                if (existingCreatedAt != null) {
                    payload["createdAt"] = existingCreatedAt
                }
            }
            payload["updatedAt"] = FieldValue.serverTimestamp()

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile", e)
            Result.failure(e)
        }
    }

    // --- Meals ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMealsForDate(date: String): Flow<List<MealItem>> = currentUidFlow().flatMapLatest { uid ->
        db.collection("users").document(uid).collection("meals")
            .whereEqualTo("date", date)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(MealItem::class.java)?.copy(id = doc.id, mealId = doc.id)
                }.sortedByDescending { it.createdAt?.toDate() }
            }
    }.catch { e ->
        Log.e(TAG, "Error observing meals for date $date", e)
        throw e
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllMeals(): Flow<List<MealItem>> = currentUidFlow().flatMapLatest { uid ->
        db.collection("users").document(uid).collection("meals")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(MealItem::class.java)?.copy(id = doc.id, mealId = doc.id)
                }.sortedByDescending { it.date }
            }
    }.catch { e ->
        Log.e(TAG, "Error observing all meals", e)
        throw e
    }

    suspend fun addMeal(meal: MealItem): Result<String> {
        return try {
            val uid = requireUserId()
            val mealId = if (meal.mealId.isNotBlank()) meal.mealId else "meal_${UUID.randomUUID()}"
            val docRef = db.collection("users").document(uid).collection("meals").document(mealId)
            val payload = meal.toMap().toMutableMap()
            payload["mealId"] = mealId
            payload["userId"] = uid
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["updatedAt"] = FieldValue.serverTimestamp()

            docRef.set(payload).await()
            Result.success(mealId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error adding meal", e)
            Result.failure(e)
        }
    }

    suspend fun updateMeal(meal: MealItem): Result<Unit> {
        return try {
            requireUserId()
            val mealId = meal.id.ifBlank { meal.mealId }
            require(mealId.isNotBlank()) { "Måltids-ID saknas" }
            val docRef = db.collection("users").document(requireUserId()).collection("meals").document(mealId)
            val updates = mapOf<String, Any?>(
                "mealId" to mealId,
                "date" to meal.date,
                "mealType" to meal.mealType,
                "name" to meal.name,
                "calories" to meal.calories,
                "protein" to meal.protein,
                "carbs" to meal.carbs,
                "fat" to meal.fat,
                "fiber" to meal.fiber,
                "sugar" to meal.sugar,
                "salt" to meal.salt,
                "itemsJson" to meal.itemsJson,
                "aiAnalyzed" to meal.aiAnalyzed,
                "imageUrl" to meal.imageUrl,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error updating meal", e)
            Result.failure(e)
        }
    }

    suspend fun deleteMeal(mealId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("users").document(uid).collection("meals").document(mealId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting meal $mealId", e)
            Result.failure(e)
        }
    }

    // --- Water Logs ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWaterLogsForDate(date: String): Flow<List<WaterLog>> = currentUidFlow().flatMapLatest { uid ->
        db.collection("users").document(uid).collection("water_logs")
            .whereEqualTo("date", date)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WaterLog::class.java)?.copy(id = doc.id, logId = doc.id)
                }
            }
    }.catch { e ->
        Log.e(TAG, "Error observing water logs", e)
        throw e
    }

    suspend fun logWater(amountMl: Int, date: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val logId = "water_${UUID.randomUUID()}"
            val docRef = db.collection("users").document(uid).collection("water_logs").document(logId)
            val payload = mapOf(
                "logId" to logId,
                "userId" to uid,
                "date" to date,
                "amountMl" to amountMl,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error logging water", e)
            Result.failure(e)
        }
    }

    suspend fun deleteWaterLog(logId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("users").document(uid).collection("water_logs").document(logId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting water log $logId", e)
            Result.failure(e)
        }
    }

    // --- Weight Logs ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWeightLogs(): Flow<List<com.example.data.model.WeightLog>> = currentUidFlow().flatMapLatest { uid ->
        db.collection("users").document(uid).collection("weight_logs")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(com.example.data.model.WeightLog::class.java)?.copy(id = doc.id, logId = doc.id)
                }.sortedBy { it.date }
            }
    }.catch { e ->
        Log.e(TAG, "Error observing weight logs", e)
        throw e
    }

    suspend fun logWeight(weightKg: Double, date: String, note: String = ""): Result<String> {
        return try {
            val uid = requireUserId()
            // Check if there is already a log for this date to update or insert
            val existing = db.collection("users").document(uid).collection("weight_logs")
                .whereEqualTo("date", date)
                .get()
                .await()

            val logId = if (!existing.isEmpty) {
                existing.documents.first().id
            } else {
                "weight_${UUID.randomUUID()}"
            }

            val docRef = db.collection("users").document(uid).collection("weight_logs").document(logId)
            val payload = mutableMapOf<String, Any?>(
                "logId" to logId,
                "userId" to uid,
                "date" to date,
                "weightKg" to weightKg,
                "note" to note
            )
            if (existing.isEmpty) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            } else {
                val existingCreatedAt = existing.documents.first().getTimestamp("createdAt")
                payload["createdAt"] = existingCreatedAt ?: FieldValue.serverTimestamp()
            }

            docRef.set(payload).await()

            // Also update current weightKg in UserProfile
            try {
                db.collection("users").document(uid).update(
                    mapOf(
                        "weightKg" to weightKg,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Could not update profile weightKg", e)
            }

            Result.success(logId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error logging weight", e)
            Result.failure(e)
        }
    }

    suspend fun deleteWeightLog(logId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("users").document(uid).collection("weight_logs").document(logId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting weight log $logId", e)
            Result.failure(e)
        }
    }

    // --- Workout Logs ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWorkoutLogsForDate(date: String): Flow<List<WorkoutLog>> = currentUidFlow().flatMapLatest { uid ->
        db.collection("users").document(uid).collection("workout_logs")
            .whereEqualTo("date", date)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WorkoutLog::class.java)?.copy(id = doc.id, logId = doc.id)
                }.sortedByDescending { it.createdAt?.toDate() }
            }
    }.catch { e ->
        Log.e(TAG, "Error observing workout logs for date $date", e)
        throw e
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllWorkoutLogs(): Flow<List<WorkoutLog>> = currentUidFlow().flatMapLatest { uid ->
        db.collection("users").document(uid).collection("workout_logs")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WorkoutLog::class.java)?.copy(id = doc.id, logId = doc.id)
                }.sortedByDescending { it.date }
            }
    }.catch { e ->
        Log.e(TAG, "Error observing all workout logs", e)
        throw e
    }

    suspend fun addWorkoutLog(workout: WorkoutLog): Result<String> {
        return try {
            val uid = requireUserId()
            val logId = if (workout.logId.isNotBlank()) workout.logId else "workout_${UUID.randomUUID()}"
            val docRef = db.collection("users").document(uid).collection("workout_logs").document(logId)
            val payload = workout.toMap().toMutableMap()
            payload["logId"] = logId
            payload["userId"] = uid
            payload["createdAt"] = FieldValue.serverTimestamp()

            docRef.set(payload).await()
            Result.success(logId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error adding workout log", e)
            Result.failure(e)
        }
    }

    suspend fun deleteWorkoutLog(logId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("users").document(uid).collection("workout_logs").document(logId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting workout log $logId", e)
            Result.failure(e)
        }
    }

    // --- Recipes ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRecipes(): Flow<List<Recipe>> = currentUidFlow().flatMapLatest { uid ->
        db.collection("users").document(uid).collection("recipes")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Recipe::class.java)?.copy(id = doc.id, recipeId = doc.id)
                }.sortedByDescending { it.updatedAt?.toDate() ?: it.createdAt?.toDate() }
            }
    }.catch { e ->
        Log.e(TAG, "Error observing recipes", e)
        throw e
    }

    suspend fun saveRecipe(recipe: Recipe): Result<String> {
        return try {
            val uid = requireUserId()
            val recipeId = if (recipe.recipeId.isNotBlank()) recipe.recipeId else "recipe_${UUID.randomUUID()}"
            val docRef = db.collection("users").document(uid).collection("recipes").document(recipeId)
            val snapshot = docRef.get().await()
            val payload = recipe.toMap().toMutableMap()
            payload["recipeId"] = recipeId
            payload["userId"] = uid
            if (!snapshot.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            } else {
                payload["createdAt"] = snapshot.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            }
            payload["updatedAt"] = FieldValue.serverTimestamp()

            docRef.set(payload).await()
            Result.success(recipeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recipe", e)
            Result.failure(e)
        }
    }

    suspend fun deleteRecipe(recipeId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("users").document(uid).collection("recipes").document(recipeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting recipe $recipeId", e)
            Result.failure(e)
        }
    }

    suspend fun addMealsBatch(meals: List<MealItem>): Result<Int> {
        return try {
            val uid = requireUserId()
            if (meals.isEmpty()) return Result.success(0)
            val batch = db.batch()
            meals.forEach { meal ->
                val mealId = if (meal.mealId.isNotBlank()) meal.mealId else "meal_${UUID.randomUUID()}"
                val docRef = db.collection("users").document(uid).collection("meals").document(mealId)
                val payload = meal.toMap().toMutableMap()
                payload["mealId"] = mealId
                payload["userId"] = uid
                payload["createdAt"] = FieldValue.serverTimestamp()
                payload["updatedAt"] = FieldValue.serverTimestamp()
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(meals.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error adding batch meals", e)
            Result.failure(e)
        }
    }

    // --- AI Corrections & Personal Learning Memory ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAiCorrections(): Flow<List<AiFoodCorrection>> = currentUidFlow().flatMapLatest { uid ->
        db.collection("users").document(uid).collection("ai_corrections")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(AiFoodCorrection::class.java)?.copy(id = doc.id, correctionId = doc.id)
                }.sortedByDescending { it.createdAt?.toDate() }
            }
    }.catch { e ->
        Log.e(TAG, "Error observing AI corrections", e)
        throw e
    }

    suspend fun saveAiCorrection(correction: AiFoodCorrection): Result<String> {
        return try {
            val uid = requireUserId()
            val correctionId = if (correction.correctionId.isNotBlank()) {
                correction.correctionId
            } else {
                "corr_${UUID.randomUUID()}"
            }
            val docRef = db.collection("users").document(uid).collection("ai_corrections").document(correctionId)
            val payload = correction.toMap().toMutableMap()
            payload["correctionId"] = correctionId
            payload["userId"] = uid
            payload["createdAt"] = FieldValue.serverTimestamp()
            docRef.set(payload).await()
            Result.success(correctionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error saving AI correction", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAiCorrection(correctionId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            db.collection("users").document(uid).collection("ai_corrections").document(correctionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting AI correction $correctionId", e)
            Result.failure(e)
        }
    }

    // --- Calculation Helper ---

    companion object {
        fun calculateNutritionTargets(
            age: Int,
            heightCm: Int,
            weightKg: Double,
            activityLevel: String,
            goalType: String
        ): NutritionTargets {
            // Mifflin-St Jeor formula (Standard baseline)
            val bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5

            val activityMultiplier = when (activityLevel) {
                "sedentary" -> 1.2
                "light" -> 1.375
                "moderate" -> 1.55
                "active" -> 1.725
                "very_active" -> 1.9
                else -> 1.55
            }

            var tdee = bmr * activityMultiplier

            when (goalType) {
                "lose_weight" -> tdee -= 500.0 // 500 kcal underskott
                "gain_weight" -> tdee += 400.0 // 400 kcal överskott
                "build_muscle" -> tdee += 250.0 // 250 kcal överskott
                "eat_healthier" -> { /* maintain */ }
                else -> { /* maintain */ }
            }

            val calories = tdee.toInt().coerceIn(1200, 6000)

            // Protein: 2.0g per kg for muscle/loss, 1.6g for maintain
            val proteinG = when (goalType) {
                "build_muscle", "lose_weight" -> (weightKg * 2.0).toInt()
                else -> (weightKg * 1.6).toInt()
            }.coerceIn(50, 350)

            // Fat: ~28% of calories (9 kcal/g)
            val fatG = ((calories * 0.28) / 9.0).toInt().coerceIn(30, 200)

            // Carbs: remainder (4 kcal/g)
            val carbCalories = calories - (proteinG * 4) - (fatG * 9)
            val carbsG = (carbCalories / 4).coerceAtLeast(50)

            // Water: ~35 ml per kg body weight
            val waterMl = (weightKg * 35).toInt().coerceIn(1500, 4500)

            return NutritionTargets(calories, proteinG, carbsG, fatG, waterMl)
        }
    }
}

data class NutritionTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val waterMl: Int
)
