package com.example.ui.stats

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class StreakMilestoneInfo(
    val currentStreakDays: Int,
    val nextMilestoneDays: Int,
    val milestoneTitle: String,
    val rewardBadge: String,
    val progressToNextMilestone: Float
)

object StreakManager {

    val milestoneTiers = listOf(3, 7, 14, 30, 50, 100, 365)

    fun calculateStreak(loggedDates: Set<String>): Int {
        if (loggedDates.isEmpty()) return 0
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        var currentCheck = today
        if (!loggedDates.contains(currentCheck.format(formatter))) {
            // Check if yesterday was logged
            currentCheck = today.minusDays(1)
            if (!loggedDates.contains(currentCheck.format(formatter))) {
                return 0
            }
        }

        var streak = 0
        while (loggedDates.contains(currentCheck.format(formatter))) {
            streak++
            currentCheck = currentCheck.minusDays(1)
        }
        return streak
    }

    fun getMilestoneInfo(streakDays: Int): StreakMilestoneInfo {
        val nextMilestone = milestoneTiers.firstOrNull { it > streakDays } ?: (streakDays + 30)
        val prevMilestone = milestoneTiers.lastOrNull { it <= streakDays } ?: 0
        val span = (nextMilestone - prevMilestone).toFloat().coerceAtLeast(1f)
        val progress = ((streakDays - prevMilestone).toFloat() / span).coerceIn(0f, 1f)

        val (title, badge) = when {
            streakDays >= 100 -> "Hundra dagars mästare" to "🏆 Guldmedalj"
            streakDays >= 50 -> "Månadernas hjälte" to "💎 Diamantbadge"
            streakDays >= 30 -> "En månad i rad" to "⭐ Stjärnstatus"
            streakDays >= 14 -> "Två veckors vanekrigare" to "🔥 Eldflamma II"
            streakDays >= 7 -> "Första veckan avklarad" to "🏅 Silverbadge"
            streakDays >= 3 -> "God start (3 dagar)" to "🌱 Grodd-badge"
            else -> "Starta din svit" to "🎯 Nybörjare"
        }

        return StreakMilestoneInfo(
            currentStreakDays = streakDays,
            nextMilestoneDays = nextMilestone,
            milestoneTitle = title,
            rewardBadge = badge,
            progressToNextMilestone = progress
        )
    }
}
