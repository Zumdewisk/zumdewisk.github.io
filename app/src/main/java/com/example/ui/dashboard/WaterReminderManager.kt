package com.example.ui.dashboard

import java.time.LocalTime

object WaterReminderManager {

    data class WaterSmartAdvice(
        val shouldRemind: Boolean,
        val suggestionTitle: String,
        val suggestionMessage: String
    )

    fun evaluateSmartWaterAdvice(
        currentMl: Int,
        targetMl: Int,
        steps: Long,
        workoutsCount: Int
    ): WaterSmartAdvice {
        val now = LocalTime.now()
        val hour = now.hour

        // Target adjustment based on activity (steps > 10000 or workouts > 0 adds 500ml requirement)
        val adjustedTarget = if (steps > 10000L || workoutsCount > 0) targetMl + 500 else targetMl
        val safeTarget = adjustedTarget.coerceAtLeast(1000)

        // Expected fraction of day passed (assuming active hours 08:00 to 22:00 = 14 hours)
        val expectedFraction = when {
            hour < 9 -> 0.1f
            hour < 12 -> 0.35f
            hour < 15 -> 0.55f
            hour < 18 -> 0.75f
            hour < 21 -> 0.9f
            else -> 1.0f
        }

        val expectedMl = (safeTarget * expectedFraction).toInt()
        val deficit = expectedMl - currentMl

        if (deficit > 300) {
            val reason = if (steps > 8000 || workoutsCount > 0) {
                "Med dina aktiva steg och träning idag behöver kroppen extra vätska."
            } else {
                "Du ligger lite efter ditt vätskeintag för den här tiden på dagen."
            }
            return WaterSmartAdvice(
                shouldRemind = true,
                suggestionTitle = "Smart vattenpåminnelse 💧",
                suggestionMessage = "$reason Du rekommenderas att dricka ett glas vatten (ca ${minOf(deficit, 500)} ml) nu."
            )
        }

        return WaterSmartAdvice(
            shouldRemind = false,
            suggestionTitle = "Vätskebalans god",
            suggestionMessage = "Ditt vattenintag är i fas med dagens aktivitetsnivå!"
        )
    }
}
