package com.example.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import com.example.ads.AdManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import com.example.data.model.MealItem
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutLog
import com.example.data.repository.AiMealAnalysisResult
import com.example.data.repository.AiNutritionService
import com.example.data.repository.NutritionRepository
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectDailyData
import com.example.health.HealthConnectManager
import com.example.ui.activity.ActivityAndWorkoutScreen
import com.example.ui.assistant.AiAssistantScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.AdMobBannerAd
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.diary.FoodDiaryScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.recipes.RecipesAndMealPlanScreen
import com.example.ui.scanner.FoodScannerScreen
import com.example.ui.scanner.ManualFoodEntryScreen
import com.example.ui.scanner.MealAnalysisReviewScreen
import com.example.ui.stats.StatsScreen
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.update.AppUpdateManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AppNavigation(
    currentUserId: String,
    aiNutritionService: AiNutritionService? = null,
    appUpdateManager: AppUpdateManager? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val repository = remember(currentUserId) { NutritionRepository(context) }
    val effectiveAiNutritionService = aiNutritionService ?: remember {
        AiNutritionService()
    }

    // Date state
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val swedishShortFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("sv-SE"))
    }
    var selectedDateObj by remember { mutableStateOf(LocalDate.now()) }
    val selectedDateStr = remember(selectedDateObj) { selectedDateObj.format(dateFormatter) }
    val previousDateStr = remember(selectedDateObj) { selectedDateObj.minusDays(1).format(dateFormatter) }

    val formattedDisplayDate = remember(selectedDateObj) {
        val today = LocalDate.now()
        val shortDate = selectedDateObj.format(swedishShortFormatter).replace(".", "")
        when (selectedDateObj) {
            today -> "Idag, $shortDate"
            today.minusDays(1) -> "Igår, $shortDate"
            today.plusDays(1) -> "Imorgon, $shortDate"
            else -> shortDate
        }
    }

    // Flows
    val userProfile by repository.observeUserProfile().collectAsState(initial = null)
    val todayMeals by repository.observeMealsForDate(selectedDateStr).collectAsState(initial = emptyList())
    val allMeals by repository.observeAllMeals().collectAsState(initial = emptyList())
    val todayWaterLogs by repository.observeWaterLogsForDate(selectedDateStr).collectAsState(initial = emptyList())
    val currentWaterMl = remember(todayWaterLogs) { todayWaterLogs.sumOf { it.amountMl } }
    val weightLogs by repository.observeWeightLogs().collectAsState(initial = emptyList())
    val todayWorkouts by repository.observeWorkoutLogsForDate(selectedDateStr).collectAsState(initial = emptyList())
    val recipes by repository.observeRecipes().collectAsState(initial = emptyList())
    val aiCorrections by repository.observeAiCorrections().collectAsState(initial = emptyList())

    LaunchedEffect(aiCorrections) {
        effectiveAiNutritionService.updateLearnedCorrections(aiCorrections)
    }

    val latestWeightKg = remember(weightLogs, userProfile) {
        weightLogs.maxByOrNull { it.date }?.weightKg ?: userProfile?.weightKg
    }

    // Health Connect state
    val healthConnectManager = remember(context) { HealthConnectManager(context) }
    var hcAvailability by remember { mutableStateOf(healthConnectManager.checkAvailability()) }
    var hasHcReadPermission by remember { mutableStateOf(false) }
    var hasHcWritePermission by remember { mutableStateOf(false) }
    var isSyncingHc by remember { mutableStateOf(false) }
    var hcDailyData by remember { mutableStateOf<HealthConnectDailyData?>(null) }

    val refreshHealthConnectData: (LocalDate) -> Unit = { targetDate ->
        coroutineScope.launch {
            hcAvailability = healthConnectManager.checkAvailability()
            if (hcAvailability == HealthConnectAvailability.AVAILABLE) {
                hasHcReadPermission = healthConnectManager.hasReadPermissions()
                hasHcWritePermission = healthConnectManager.hasWritePermissions()
                if (hasHcReadPermission) {
                    isSyncingHc = true
                    hcDailyData = healthConnectManager.fetchDailyData(targetDate)
                    isSyncingHc = false
                } else {
                    hcDailyData = null
                }
            } else {
                hasHcReadPermission = false
                hasHcWritePermission = false
                hcDailyData = null
            }
        }
    }

    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = healthConnectManager.createRequestPermissionsContract()
    ) { granted ->
        hasHcReadPermission = granted.containsAll(healthConnectManager.readPermissions)
        hasHcWritePermission = granted.containsAll(healthConnectManager.allPermissions)
        if (hasHcReadPermission) {
            refreshHealthConnectData(selectedDateObj)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Health Connect anslutet och synkat!")
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Behörighet till Health Connect nekades")
            }
        }
    }

    LaunchedEffect(selectedDateObj, currentUserId) {
        refreshHealthConnectData(selectedDateObj)
    }

    val activityEnergySummary = remember(userProfile, latestWeightKg, hcDailyData, todayWorkouts) {
        HealthConnectManager.calculateDailyActivityEnergy(
            baseCalorieGoal = userProfile?.targetCalories ?: 2200,
            weightKg = latestWeightKg ?: 75.0,
            healthConnectData = hcDailyData,
            manualWorkouts = todayWorkouts
        )
    }

    val screenSaver = remember {
        Saver<Screen, String>(
            save = { it.route },
            restore = { route ->
                when (route) {
                    Screen.LogFood.route -> Screen.LogFood
                    Screen.Diary.route -> Screen.Diary
                    Screen.Stats.route -> Screen.Stats
                    Screen.Profile.route -> Screen.Profile
                    Screen.Weight.route -> Screen.Weight
                    Screen.Activity.route -> Screen.Activity
                    Screen.RecipesAndPlans.route -> Screen.RecipesAndPlans
                    Screen.AiAssistant.route -> Screen.AiAssistant
                    Screen.ManualEntry.route -> Screen.ManualEntry
                    Screen.AnalysisReview.route -> Screen.AnalysisReview
                    else -> Screen.Dashboard
                }
            }
        )
    }
    var currentScreen by rememberSaveable(stateSaver = screenSaver) { mutableStateOf<Screen>(Screen.Dashboard) }

    // Scanner / Review transition state & origin screen return tracking
    var pendingAnalysisResult by remember { mutableStateOf<AiMealAnalysisResult?>(null) }
    var pendingMealImageUrl by rememberSaveable { mutableStateOf("") }
    var selectedMealCategoryForAdd by rememberSaveable { mutableStateOf("lunch") }
    var mealEntryOriginScreen by rememberSaveable(stateSaver = screenSaver) { mutableStateOf<Screen>(Screen.Dashboard) }
    var diaryFocusCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var diaryFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    var autoLaunchCamera by rememberSaveable { mutableStateOf(false) }
    var autoLaunchGallery by rememberSaveable { mutableStateOf(false) }

    val navigateAfterMealEntry: (String?) -> Unit = { categoryToFocus ->
        autoLaunchCamera = false
        autoLaunchGallery = false
        if (mealEntryOriginScreen == Screen.Diary) {
            diaryFocusCategory = categoryToFocus ?: selectedMealCategoryForAdd
            diaryFocusTrigger++
            currentScreen = Screen.Diary
        } else {
            currentScreen = Screen.Dashboard
        }
        mealEntryOriginScreen = Screen.Dashboard
    }

    val openVoiceMealReview: (AiMealAnalysisResult, String) -> Unit = { analysis, chosenMealType ->
        selectedMealCategoryForAdd = chosenMealType
        pendingAnalysisResult = analysis
        pendingMealImageUrl = ""
        AiNutritionService.lastAnalysisResult = analysis
        AiNutritionService.lastImageUrl = ""
        autoLaunchCamera = false
        autoLaunchGallery = false
        currentScreen = Screen.AnalysisReview
    }

    // Initialize initial user profile if non-existent
    androidx.compose.runtime.LaunchedEffect(currentUserId) {
        val existingProfile = repository.getUserProfile().getOrNull()
        if (existingProfile == null) {
            val authUser = Firebase.auth.currentUser
            val initialTargets = NutritionRepository.calculateNutritionTargets(28, 178, 75.0, "moderate", "maintain")
            val defaultProfile = UserProfile(
                userId = currentUserId,
                email = authUser?.email ?: "",
                displayName = authUser?.displayName ?: "Användare",
                age = 28,
                heightCm = 178,
                weightKg = 75.0,
                activityLevel = "moderate",
                goalType = "maintain",
                targetCalories = initialTargets.calories,
                targetProteinG = initialTargets.proteinG,
                targetCarbsG = initialTargets.carbsG,
                targetFatG = initialTargets.fatG,
                targetWaterMl = initialTargets.waterMl
            )
            repository.saveUserProfile(defaultProfile)
        }
    }

    // Back handlers for secondary screens
    if (currentScreen != Screen.Dashboard) {
        BackHandler {
            when (currentScreen) {
                Screen.AnalysisReview -> {
                    pendingAnalysisResult = null
                    AiNutritionService.lastAnalysisResult = null
                    AiNutritionService.lastImageUrl = ""
                    navigateAfterMealEntry(null)
                }
                Screen.LogFood, Screen.ManualEntry -> {
                    navigateAfterMealEntry(null)
                }
                else -> {
                    currentScreen = Screen.Dashboard
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentWindowInsets = WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val isMainTab = currentScreen in listOf(Screen.Dashboard, Screen.Diary, Screen.Stats, Screen.Profile)
                    if (isMainTab) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                        val activity = context as? Activity

                        // 1. Hem
                        NavigationBarItem(
                            selected = currentScreen == Screen.Dashboard,
                            onClick = {
                                if (currentScreen != Screen.Dashboard) {
                                    currentScreen = Screen.Dashboard
                                    activity?.let { AdManager.onTabSwitched(it) }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentScreen == Screen.Dashboard) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Hem"
                                )
                            },
                            label = { Text("Hem", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = EmeraldPrimary.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.testTag("nav_item_home")
                        )

                        // 2. Dagbok
                        NavigationBarItem(
                            selected = currentScreen == Screen.Diary,
                            onClick = {
                                if (currentScreen != Screen.Diary) {
                                    currentScreen = Screen.Diary
                                    activity?.let { AdManager.onTabSwitched(it) }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentScreen == Screen.Diary) Icons.Filled.Book else Icons.Outlined.Book,
                                    contentDescription = "Dagbok"
                                )
                            },
                            label = { Text("Dagbok", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = EmeraldPrimary.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.testTag("nav_item_diary")
                        )

                        // 3. Logga (Center Prominent Camera Button)
                        NavigationBarItem(
                            selected = currentScreen == Screen.LogFood,
                            onClick = {
                                mealEntryOriginScreen = Screen.Dashboard
                                autoLaunchCamera = true
                                autoLaunchGallery = false
                                if (currentScreen != Screen.LogFood) {
                                    currentScreen = Screen.LogFood
                                    activity?.let { AdManager.onTabSwitched(it) }
                                }
                            },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .shadow(6.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Fotografera mat",
                                        tint = Color.Black,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    "Logga",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_item_log_camera")
                        )

                        // 4. Statistik
                        NavigationBarItem(
                            selected = currentScreen == Screen.Stats,
                            onClick = {
                                if (currentScreen != Screen.Stats) {
                                    currentScreen = Screen.Stats
                                    activity?.let { AdManager.onTabSwitched(it) }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentScreen == Screen.Stats) Icons.Filled.BarChart else Icons.Outlined.BarChart,
                                    contentDescription = "Statistik"
                                )
                            },
                            label = { Text("Statistik", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = EmeraldPrimary.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.testTag("nav_item_stats")
                        )

                        // 5. Profil
                        NavigationBarItem(
                            selected = currentScreen == Screen.Profile,
                            onClick = {
                                if (currentScreen != Screen.Profile) {
                                    currentScreen = Screen.Profile
                                    activity?.let { AdManager.onTabSwitched(it) }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentScreen == Screen.Profile) Icons.Filled.Person else Icons.Outlined.Person,
                                    contentDescription = "Profil"
                                )
                            },
                            label = { Text("Profil", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = EmeraldPrimary.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.testTag("nav_item_profile")
                        )
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                )
                AdMobBannerAd(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                is Screen.Dashboard -> {
                    DashboardScreen(
                        currentDate = selectedDateStr,
                        formattedDateDisplay = formattedDisplayDate,
                        onPreviousDay = { selectedDateObj = selectedDateObj.minusDays(1) },
                        onNextDay = { selectedDateObj = selectedDateObj.plusDays(1) },
                        userProfile = userProfile,
                        meals = todayMeals,
                        waterMl = currentWaterMl,
                        onAddWater = { ml ->
                            coroutineScope.launch {
                                repository.logWater(ml, selectedDateStr)
                                snackbarHostState.showSnackbar("+$ml ml vatten registrerat!")
                            }
                        },
                        onOpenAiCamera = {
                            mealEntryOriginScreen = Screen.Dashboard
                            autoLaunchCamera = true
                            autoLaunchGallery = false
                            currentScreen = Screen.LogFood
                        },
                        onOpenAiGallery = {
                            mealEntryOriginScreen = Screen.Dashboard
                            autoLaunchCamera = false
                            autoLaunchGallery = true
                            currentScreen = Screen.LogFood
                        },
                        onOpenAiAssistant = {
                            currentScreen = Screen.AiAssistant
                        },
                        onLogMealCategory = { category ->
                            mealEntryOriginScreen = Screen.Dashboard
                            selectedMealCategoryForAdd = category
                            currentScreen = Screen.ManualEntry
                        },
                        onNavigateToDiary = {
                            currentScreen = Screen.Diary
                        },
                        latestWeightKg = latestWeightKg,
                        onNavigateToWeight = {
                            currentScreen = Screen.Weight
                        },
                        activitySummary = activityEnergySummary,
                        todayWorkoutsCount = todayWorkouts.size + (hcDailyData?.workouts?.count { !it.isFromNaringAi } ?: 0),
                        hasHealthConnectPermission = hasHcReadPermission,
                        onNavigateToActivity = {
                            currentScreen = Screen.Activity
                        },
                        savedRecipesCount = recipes.size,
                        onNavigateToRecipesAndPlans = {
                            currentScreen = Screen.RecipesAndPlans
                        }
                    )
                }

                is Screen.Diary -> {
                    FoodDiaryScreen(
                        currentDate = selectedDateStr,
                        formattedDateDisplay = formattedDisplayDate,
                        onPreviousDay = { selectedDateObj = selectedDateObj.minusDays(1) },
                        onNextDay = { selectedDateObj = selectedDateObj.plusDays(1) },
                        meals = todayMeals,
                        onAddMealWithAi = { cat ->
                            mealEntryOriginScreen = Screen.Diary
                            selectedMealCategoryForAdd = cat
                            autoLaunchCamera = true
                            autoLaunchGallery = false
                            currentScreen = Screen.LogFood
                        },
                        onAddMealManual = { cat ->
                            mealEntryOriginScreen = Screen.Diary
                            selectedMealCategoryForAdd = cat
                            currentScreen = Screen.ManualEntry
                        },
                        onDeleteMeal = { mealId ->
                            coroutineScope.launch {
                                repository.deleteMeal(mealId)
                                snackbarHostState.showSnackbar("Måltid raderades")
                            }
                        },
                        onCopyMeal = { meal, targetDate ->
                            coroutineScope.launch {
                                val copied = meal.copy(
                                    mealId = "",
                                    date = targetDate
                                )
                                repository.addMeal(copied)
                                snackbarHostState.showSnackbar("Måltid kopierades till $targetDate")
                            }
                        },
                        onUpdateMeal = { updatedMeal ->
                            coroutineScope.launch {
                                val res = repository.updateMeal(updatedMeal)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Måltid uppdaterad (${updatedMeal.calories} kcal)")
                                } else {
                                    snackbarHostState.showSnackbar("Kunde inte uppdatera måltid: ${res.exceptionOrNull()?.localizedMessage ?: "Okänt fel"}")
                                }
                            }
                        },
                        aiNutritionService = effectiveAiNutritionService,
                        learnedCorrections = aiCorrections,
                        onCorrectionLearned = { correction ->
                            coroutineScope.launch {
                                val res = repository.saveAiCorrection(correction)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("AI:n har lärt sig och uppdaterat måltiden!")
                                } else {
                                    snackbarHostState.showSnackbar("Måltid uppdaterad, men minnesregeln kunde inte sparas.")
                                }
                            }
                        },
                        onDeleteLearnedRule = { correctionId ->
                            coroutineScope.launch {
                                val res = repository.deleteAiCorrection(correctionId)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Inlärd AI-regel borttagen")
                                }
                            }
                        },
                        focusCategory = diaryFocusCategory,
                        focusTrigger = diaryFocusTrigger,
                        allMeals = allMeals,
                        previousDateStr = previousDateStr,
                        onCopyMealsBatch = { mealsToCopy, targetDate, label ->
                            coroutineScope.launch {
                                val copies = mealsToCopy.map {
                                    it.copy(
                                        id = "",
                                        mealId = "",
                                        date = targetDate,
                                        createdAt = null,
                                        updatedAt = null
                                    )
                                }
                                val res = repository.addMealsBatch(copies)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("$label (${copies.size} st) kopierades!")
                                } else {
                                    snackbarHostState.showSnackbar("Kunde inte kopiera måltider: ${res.exceptionOrNull()?.localizedMessage ?: "Okänt fel"}")
                                }
                            }
                        },
                        onVoiceMealAnalyzed = { analysis, chosenMealType ->
                            mealEntryOriginScreen = Screen.Diary
                            openVoiceMealReview(analysis, chosenMealType)
                        }
                    )
                }

                is Screen.Stats -> {
                    StatsScreen(
                        userProfile = userProfile,
                        allMeals = allMeals,
                        onSelectDay = { dateStr ->
                            try {
                                selectedDateObj = LocalDate.parse(dateStr, dateFormatter)
                                currentScreen = Screen.Diary
                            } catch (e: Exception) {
                                // fallback
                            }
                        },
                        onNavigateToWeight = {
                            currentScreen = Screen.Weight
                        }
                    )
                }

                is Screen.Weight -> {
                    com.example.ui.weight.WeightTrackingScreen(
                        userProfile = userProfile,
                        weightLogs = weightLogs,
                        onLogWeight = { weight, date, note ->
                            coroutineScope.launch {
                                repository.logWeight(weight, date, note)
                                snackbarHostState.showSnackbar("Vikt $weight kg sparad!")
                            }
                        },
                        onDeleteLog = { logId ->
                            coroutineScope.launch {
                                repository.deleteWeightLog(logId)
                                snackbarHostState.showSnackbar("Viktlogg raderades")
                            }
                        },
                        onBack = {
                            currentScreen = Screen.Dashboard
                        }
                    )
                }

                is Screen.Activity -> {
                    ActivityAndWorkoutScreen(
                        currentDate = selectedDateStr,
                        formattedDateDisplay = formattedDisplayDate,
                        userWeightKg = latestWeightKg ?: 75.0,
                        healthConnectAvailability = hcAvailability,
                        hasHealthConnectReadPermission = hasHcReadPermission,
                        hasHealthConnectWritePermission = hasHcWritePermission,
                        isSyncingHealthConnect = isSyncingHc,
                        healthConnectData = hcDailyData,
                        activityEnergySummary = activityEnergySummary,
                        manualWorkouts = todayWorkouts,
                        onRequestHealthConnectPermissions = {
                            hcPermissionLauncher.launch(healthConnectManager.allPermissions)
                        },
                        onOpenHealthConnectSettings = {
                            healthConnectManager.openHealthConnectSettingsOrInstall(context)
                        },
                        onRefreshHealthConnect = {
                            refreshHealthConnectData(selectedDateObj)
                        },
                        onLogWorkout = { workoutType, title, durationMinutes, caloriesBurned, notes, syncToHc ->
                            coroutineScope.launch {
                                val clientRecordId = java.util.UUID.randomUUID().toString()
                                var synced = false
                                if (syncToHc && hasHcWritePermission) {
                                    val hcRes = healthConnectManager.writeWorkoutSession(
                                        clientRecordId = clientRecordId,
                                        workoutType = workoutType,
                                        title = title,
                                        durationMinutes = durationMinutes,
                                        caloriesBurned = caloriesBurned,
                                        date = selectedDateObj,
                                        notes = notes
                                    )
                                    synced = hcRes.isSuccess
                                }

                                val workout = WorkoutLog(
                                    logId = "workout_$clientRecordId",
                                    date = selectedDateStr,
                                    workoutType = workoutType.id,
                                    title = title,
                                    durationMinutes = durationMinutes,
                                    caloriesBurned = caloriesBurned,
                                    notes = notes,
                                    syncedToHealthConnect = synced
                                )
                                val repoRes = repository.addWorkoutLog(workout)
                                if (repoRes.isSuccess) {
                                    if (synced) refreshHealthConnectData(selectedDateObj)
                                    snackbarHostState.showSnackbar("Träningspass sparat (+$caloriesBurned kcal till dagens mål)!")
                                } else {
                                    snackbarHostState.showSnackbar("Kunde inte spara träningspass: ${repoRes.exceptionOrNull()?.localizedMessage ?: "Okänt fel"}")
                                }
                            }
                        },
                        onDeleteWorkout = { logId ->
                            coroutineScope.launch {
                                repository.deleteWorkoutLog(logId)
                                snackbarHostState.showSnackbar("Träningspass raderades")
                            }
                        },
                        onBack = {
                            currentScreen = Screen.Dashboard
                        }
                    )
                }

                is Screen.RecipesAndPlans -> {
                    RecipesAndMealPlanScreen(
                        currentDate = selectedDateStr,
                        formattedDateDisplay = formattedDisplayDate,
                        effectiveCalorieGoal = activityEnergySummary.adjustedCalorieGoal,
                        userProfile = userProfile,
                        recipes = recipes,
                        aiNutritionService = effectiveAiNutritionService,
                        onSaveRecipe = { recipe ->
                            coroutineScope.launch {
                                val res = repository.saveRecipe(recipe)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Recept '${recipe.name}' sparat!")
                                } else {
                                    snackbarHostState.showSnackbar("Kunde inte spara recept: ${res.exceptionOrNull()?.localizedMessage ?: "Okänt fel"}")
                                }
                            }
                        },
                        onDeleteRecipe = { recipeId ->
                            coroutineScope.launch {
                                val res = repository.deleteRecipe(recipeId)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Recept raderades")
                                } else {
                                    snackbarHostState.showSnackbar("Kunde inte radera recept")
                                }
                            }
                        },
                        onLogSingleMeal = { meal ->
                            coroutineScope.launch {
                                val res = repository.addMeal(meal)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("'${meal.name}' loggad i dagboken!")
                                } else {
                                    snackbarHostState.showSnackbar("Kunde inte logga måltid: ${res.exceptionOrNull()?.localizedMessage ?: "Okänt fel"}")
                                }
                            }
                        },
                        onLogFullMealPlan = { plan ->
                            coroutineScope.launch {
                                val mealsToLog = plan.toMealItems(selectedDateStr)
                                val res = repository.addMealsBatch(mealsToLog)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Hela dagsschemat (${mealsToLog.size} måltider) loggades!")
                                    currentScreen = Screen.Dashboard
                                } else {
                                    snackbarHostState.showSnackbar("Kunde inte logga kostschemat: ${res.exceptionOrNull()?.localizedMessage ?: "Okänt fel"}")
                                }
                            }
                        },
                        onBack = {
                            currentScreen = Screen.Dashboard
                        }
                    )
                }

                is Screen.Profile -> {
                    ProfileScreen(
                        currentProfile = userProfile,
                        onSaveProfile = { updated ->
                            coroutineScope.launch {
                                repository.saveUserProfile(updated)
                                snackbarHostState.showSnackbar("Profil sparad och dagsmål uppdaterade!")
                            }
                        },
                        onSignOut = {
                            val credManager = CredentialManager.create(context)
                            signOutUser(context, credManager, {
                                // Triggers auth state listener
                            }, coroutineScope)
                        },
                        aiNutritionService = effectiveAiNutritionService,
                        learnedCorrections = aiCorrections,
                        onDeleteLearnedRule = { correctionId ->
                            coroutineScope.launch {
                                val res = repository.deleteAiCorrection(correctionId)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Inlärd AI-regel borttagen")
                                }
                            }
                        },
                        appUpdateManager = appUpdateManager
                    )
                }

                is Screen.LogFood -> {
                    FoodScannerScreen(
                        aiNutritionService = effectiveAiNutritionService,
                        onAnalysisCompleted = { result, imageUrl ->
                            pendingAnalysisResult = result
                            pendingMealImageUrl = imageUrl
                            AiNutritionService.lastAnalysisResult = result
                            AiNutritionService.lastImageUrl = imageUrl
                            autoLaunchCamera = false
                            autoLaunchGallery = false
                            currentScreen = Screen.AnalysisReview
                        },
                        onManualSearchRequested = {
                            autoLaunchCamera = false
                            autoLaunchGallery = false
                            currentScreen = Screen.ManualEntry
                        },
                        onDismiss = {
                            navigateAfterMealEntry(null)
                        },
                        autoLaunchCamera = autoLaunchCamera,
                        autoLaunchGallery = autoLaunchGallery
                    )
                }

                is Screen.AnalysisReview -> {
                    val analysis = pendingAnalysisResult
                        ?: AiNutritionService.lastAnalysisResult
                        ?: effectiveAiNutritionService.getSmartFallbackAnalysis("Måltid")

                    MealAnalysisReviewScreen(
                        initialAnalysis = analysis,
                        defaultMealType = selectedMealCategoryForAdd,
                        currentDate = selectedDateStr,
                        imageUrl = pendingMealImageUrl.ifBlank { AiNutritionService.lastImageUrl },
                        onSaveMeal = { meal ->
                            coroutineScope.launch {
                                repository.addMeal(meal)
                                snackbarHostState.showSnackbar("Måltid '${meal.name}' sparad i dagboken!")
                                pendingAnalysisResult = null
                                AiNutritionService.lastAnalysisResult = null
                                AiNutritionService.lastImageUrl = ""
                                navigateAfterMealEntry(meal.mealType)
                            }
                        },
                        onCancel = {
                            pendingAnalysisResult = null
                            AiNutritionService.lastAnalysisResult = null
                            AiNutritionService.lastImageUrl = ""
                            navigateAfterMealEntry(null)
                        },
                        aiNutritionService = effectiveAiNutritionService,
                        learnedCorrections = aiCorrections,
                        onCorrectionLearned = { correction ->
                            coroutineScope.launch {
                                val res = repository.saveAiCorrection(correction)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("AI:n har lärt sig din rättelse!")
                                }
                            }
                        },
                        onDeleteLearnedRule = { correctionId ->
                            coroutineScope.launch {
                                val res = repository.deleteAiCorrection(correctionId)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Inlärd AI-regel borttagen")
                                }
                            }
                        }
                    )
                }

                is Screen.ManualEntry -> {
                    ManualFoodEntryScreen(
                        aiNutritionService = effectiveAiNutritionService,
                        currentDate = selectedDateStr,
                        initialMealType = selectedMealCategoryForAdd,
                        allMeals = allMeals,
                        onVoiceMealAnalyzed = openVoiceMealReview,
                        onSaveMeal = { meal ->
                            coroutineScope.launch {
                                repository.addMeal(meal)
                                snackbarHostState.showSnackbar("'${meal.name}' loggad!")
                                navigateAfterMealEntry(meal.mealType)
                            }
                        },
                        onBack = {
                            navigateAfterMealEntry(null)
                        }
                    )
                }

                is Screen.AiAssistant -> {
                    AiAssistantScreen(
                        aiNutritionService = effectiveAiNutritionService,
                        userProfile = userProfile,
                        todayMeals = todayMeals,
                        todayWaterMl = currentWaterMl,
                        onBack = {
                            currentScreen = Screen.Dashboard
                        }
                    )
                }
            }
        }
    }
}
}
