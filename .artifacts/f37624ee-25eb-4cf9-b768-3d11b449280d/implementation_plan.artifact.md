# Implementation Plan: Zevra Advanced Health & Nutrition Features

This plan outlines the implementation of 6 major feature enhancements for Zevra:
1. **AI Volume & Gram Estimation**: Enhanced AI vision analysis using plate proportions and reference objects for precise portion estimation (grams of protein, dl of rice, etc.).
2. **Deeper Health Connect Analysis**: Sync sleep quality and active rest in `HealthConnectManager` to provide smarter AI recommendations for energy and protein needs.
3. **Carb Cycling**: Support setting distinct nutrition goals (calories, carbs, protein) for training days versus rest days.
4. **Smart Water Reminders**: Intelligent, non-intrusive notifications based on user activity level and daily intake progress.
5. **Streaks & Milestones**: Habit tracking for consecutive logging days and goal completion, with milestone rewards and badges.
6. **Recipe & Meal Plan Export**: Easy sharing of AI-generated meal plans and favorite recipes via links and QR codes.

## User Review Required

> [!IMPORTANT]
> All features will maintain seamless backward compatibility with existing user profiles, local database schemas, and Firebase sync.

## Open Questions

- None. Proceeding with robust, clean Compose and Kotlin implementations following project patterns.

## Proposed Changes

### 1. AI Volume & Gram Estimation
#### [MODIFY] [AiNutritionService.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/data/repository/AiNutritionService.kt)
- Update vision analysis prompt and parsing instructions to explicitly analyze plate size/proportions and estimate precise volumetric quantities (dl, tablespoons) and weight in grams (e.g. protein in grams, rice in dl/grams).

#### [MODIFY] [MealAnalysisReviewScreen.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/ui/scanner/MealAnalysisReviewScreen.kt)
- Display estimated plate proportion reasoning and clear gram/volume breakdowns (e.g., "Baserat på tallrikens proportioner: ca 180g kyckling, 2.5 dl ris").

---

### 2. Deeper Health Connect Analysis
#### [MODIFY] [HealthConnectManager.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/health/HealthConnectManager.kt)
- Add reading permissions for `SleepSessionRecord` and recovery/active rest metrics.
- Implement methods to query sleep duration and sleep quality / stages.

#### [MODIFY] [AiNutritionService.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/data/repository/AiNutritionService.kt)
- Incorporate sleep quality and active rest data into AI recommendation prompts to dynamically adjust daily energy and protein recommendations.

---

### 3. Carb Cycling (Macro-cykling)
#### [MODIFY] [UserProfile.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/data/model/UserProfile.kt)
- Add carb cycling fields (`isCarbCyclingEnabled`, `restDayCalories`, `restDayProteinG`, `restDayCarbsG`, `restDayFatG`, `isTodayTrainingDay`).

#### [MODIFY] [DashboardScreen.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/ui/dashboard/DashboardScreen.kt)
- Add a training day / rest day toggle banner on the dashboard to instantly switch macro targets and show active carb cycling status.

---

### 4. Smart Water Reminders
#### [NEW] [WaterReminderManager.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/ui/dashboard/WaterReminderManager.kt)
- Implement smart reminder evaluation based on current time, daily water target, current water intake, and activity level (steps/workouts).
- Schedule non-intrusive notifications when intake is lagging behind schedule.

---

### 5. Streaks & Milestones
#### [NEW] [StreakManager.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/ui/stats/StreakManager.kt)
- Track consecutive days of logging meals or reaching targets.
- Manage milestones (3, 7, 14, 30, 50, 100 days) and rewards.

#### [MODIFY] [DashboardScreen.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/ui/dashboard/DashboardScreen.kt)
- Add a Streak & Milestones card showing current streak flame icon, days count, and next milestone reward.

---

### 6. Recipe & Meal Plan Export
#### [MODIFY] [RecipesAndMealPlanScreen.kt](file:///C:/Users/henri/StudioProjects/zumdewisk.github.io/app/src/main/java/com/example/ui/recipes/RecipesAndMealPlanScreen.kt)
- Add export and sharing action (sharing formatted recipe text / web share link / QR code dialog using simple text sharing and QR representation) for recipes and AI meal plans.

## Verification Plan

### Automated Tests
- Run Gradle build (`app:assembleDebug`) to verify compilation of all new features and modifications.

### Manual Verification
- Deploy app to emulator/device, verify AI scanner portion estimation, Health Connect sync with sleep, Carb Cycling toggle, smart water reminders, Streaks card, and Recipe/Meal Plan export.
