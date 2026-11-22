package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val age: Int = 28,
    val heightCm: Int = 178,
    val weightKg: Double = 75.0,
    val activityLevel: String = "moderate", // sedentary, light, moderate, active, very_active
    val goalType: String = "maintain",     // lose_weight, maintain, gain_weight, eat_healthier, build_muscle
    val targetCalories: Int = 2200,
    val targetProteinG: Int = 140,
    val targetCarbsG: Int = 220,
    val targetFatG: Int = 70,
    val targetWaterMl: Int = 2500,
    val isCarbCyclingEnabled: Boolean = false,
    val isTodayTrainingDay: Boolean = true,
    val restDayCalories: Int = 1900,
    val restDayProteinG: Int = 150,
    val restDayCarbsG: Int = 140,
    val restDayFatG: Int = 75,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "email" to email,
            "displayName" to displayName,
            "age" to age,
            "heightCm" to heightCm,
            "weightKg" to weightKg,
            "activityLevel" to activityLevel,
            "goalType" to goalType,
            "targetCalories" to targetCalories,
            "targetProteinG" to targetProteinG,
            "targetCarbsG" to targetCarbsG,
            "targetFatG" to targetFatG,
            "targetWaterMl" to targetWaterMl,
            "isCarbCyclingEnabled" to isCarbCyclingEnabled,
            "isTodayTrainingDay" to isTodayTrainingDay,
            "restDayCalories" to restDayCalories,
            "restDayProteinG" to restDayProteinG,
            "restDayCarbsG" to restDayCarbsG,
            "restDayFatG" to restDayFatG,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        ).filterValues { it != null }
    }
}
