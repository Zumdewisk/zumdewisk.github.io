package com.example.ui.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.FavoritesManager
import com.example.data.model.AiFoodCorrection
import com.example.data.model.FoodIngredient
import com.example.data.model.MealItem
import com.example.data.repository.AiMealAnalysisResult
import com.example.data.repository.AiNutritionService
import com.example.ui.scanner.AiCorrectionSheet
import com.example.ui.scanner.VoiceMealLogSheet
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MacroCarbs
import com.example.ui.theme.MacroFat
import com.example.ui.theme.MacroProtein
import com.example.ui.util.formatMacroDecimal
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDiaryScreen(
    currentDate: String,
    formattedDateDisplay: String,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    meals: List<MealItem>,
    onAddMealWithAi: (String) -> Unit,
    onAddMealManual: (String) -> Unit,
    onDeleteMeal: (String) -> Unit,
    onCopyMeal: (MealItem, String) -> Unit,
    onUpdateMeal: (MealItem) -> Unit = {},
    aiNutritionService: AiNutritionService? = null,
    learnedCorrections: List<AiFoodCorrection> = emptyList(),
    onCorrectionLearned: (AiFoodCorrection) -> Unit = {},
    onDeleteLearnedRule: (String) -> Unit = {},
    focusCategory: String? = null,
    focusTrigger: Int = 0,
    allMeals: List<MealItem> = emptyList(),
    previousDateStr: String = "",
    onCopyMealsBatch: (List<MealItem>, String, String) -> Unit = { batch, targetDate, _ ->
        batch.forEach { onCopyMeal(it, targetDate) }
    },
    onVoiceMealAnalyzed: ((AiMealAnalysisResult, String) -> Unit)? = null
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        FavoritesManager.ensureLoaded(context)
    }
    val favoriteFoods by FavoritesManager.favorites.collectAsState()

    var mealToDelete by remember { mutableStateOf<MealItem?>(null) }
    var mealToEdit by remember { mutableStateOf<MealItem?>(null) }
    var mealToCorrectWithAi by remember { mutableStateOf<MealItem?>(null) }
    var selectedCategoryForAdd by remember { mutableStateOf<String?>(null) }
    var voiceCategoryForSheet by remember { mutableStateOf<String?>(null) }
    var highlightedCategory by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val categories = listOf(
        Triple("frukost", "Frukost", Icons.Default.WbSunny to MacroCarbs),
        Triple("lunch", "Lunch", Icons.Default.Restaurant to EmeraldPrimary),
        Triple("middag", "Middag", Icons.Default.DinnerDining to MacroProtein),
        Triple("mellanmal", "Mellanmål", Icons.Default.BakeryDining to MacroFat),
        Triple("ovrigt", "Övrigt", Icons.Default.Restaurant to EmeraldDark)
    )

    val yesterdayMeals = remember(allMeals, previousDateStr) {
        if (previousDateStr.isBlank()) emptyList() else allMeals.filter { it.date == previousDateStr }
    }
    val yesterdayBreakfast = remember(yesterdayMeals) {
        yesterdayMeals.filter { it.mealType == "frukost" }
    }
    val recurringMeals = remember(allMeals, meals) {
        val (frequent, _) = FavoritesManager.computeFrequentAndRecentMeals(allMeals, maxFrequent = 10, maxRecent = 10)
        val todayNames = meals.map { FavoritesManager.normalizeFoodName(it.name) }.toHashSet()
        frequent
            .filter { it.latestMeal.date != currentDate || !todayNames.contains(it.canonicalKey) }
            .take(6)
    }
    val showEatAgainSection = yesterdayMeals.isNotEmpty() || recurringMeals.isNotEmpty()

    LaunchedEffect(focusTrigger, focusCategory, showEatAgainSection) {
        if (focusTrigger > 0 && !focusCategory.isNullOrBlank()) {
            val catIndex = categories.indexOfFirst { it.first == focusCategory }
            if (catIndex >= 0) {
                val headerCount = if (showEatAgainSection) 3 else 2
                listState.animateScrollToItem(headerCount + catIndex)
                highlightedCategory = focusCategory
                delay(1800L)
                highlightedCategory = null
            }
        }
    }

    val totalDayCalories = meals.sumOf { it.calories }
    val totalDayProtein = meals.sumOf { it.protein }
    val totalDayCarbs = meals.sumOf { it.carbs }
    val totalDayFat = meals.sumOf { it.fat }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Matdagbok", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Date Switcher Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPreviousDay, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Föregående dag")
                        }
                        Text(
                            text = formattedDateDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onNextDay, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Nästa dag")
                        }
                    }
                }
            }

            // Daily Total Intake Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Dagstotal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${meals.size} ${if (meals.size == 1) "måltid loggad" else "måltider loggade"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "$totalDayCalories kcal",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiaryMacroSummaryPill(
                                label = "Protein",
                                value = "${totalDayProtein.roundToInt()} g",
                                color = MacroProtein,
                                modifier = Modifier.weight(1f)
                            )
                            DiaryMacroSummaryPill(
                                label = "Kolhydrater",
                                value = "${totalDayCarbs.roundToInt()} g",
                                color = MacroCarbs,
                                modifier = Modifier.weight(1f)
                            )
                            DiaryMacroSummaryPill(
                                label = "Fett",
                                value = "${totalDayFat.roundToInt()} g",
                                color = MacroFat,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Quick "Ät igen & Gårdagens måltider" 1-tap Card
            if (showEatAgainSection) {
                item(key = "eat_again_section") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eat_again_section"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.28f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Ät igen • Snabbkopiera",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (yesterdayBreakfast.isNotEmpty() || yesterdayMeals.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (yesterdayBreakfast.isNotEmpty()) {
                                        val bKcal = yesterdayBreakfast.sumOf { it.calories }
                                        Button(
                                            onClick = {
                                                onCopyMealsBatch(yesterdayBreakfast, currentDate, "Gårdagens frukost")
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = EmeraldPrimary,
                                                contentColor = Color.Black
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("copy_yesterday_breakfast_button")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Kopiera gårdagens frukost ($bKcal kcal)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    if (yesterdayMeals.size > yesterdayBreakfast.size) {
                                        OutlinedButton(
                                            onClick = {
                                                onCopyMealsBatch(yesterdayMeals, currentDate, "Gårdagens måltider")
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f)),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("copy_yesterday_all_button")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Kopiera hela gårdagen (${yesterdayMeals.size} st)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            if (recurringMeals.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    recurringMeals.forEachIndexed { idx, summary ->
                                        val sample = summary.latestMeal
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onCopyMeal(sample, currentDate) }
                                                .testTag("eat_again_chip_$idx")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "${summary.displayName} • ${sample.calories} kcal",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (summary.count > 1) {
                                                    Text(
                                                        text = "(${summary.count}x)",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Categories
            categories.forEach { (catKey, catTitle, iconAndAccent) ->
                val categoryMeals = meals.filter { it.mealType == catKey }
                val yesterdayCategoryMeals = yesterdayMeals.filter { it.mealType == catKey }
                val catCalories = categoryMeals.sumOf { it.calories }

                item(key = "cat_$catKey") {
                    MealCategoryDiarySection(
                        title = catTitle,
                        categoryKey = catKey,
                        icon = iconAndAccent.first,
                        accentColor = iconAndAccent.second,
                        calories = catCalories,
                        meals = categoryMeals,
                        yesterdayCategoryMeals = yesterdayCategoryMeals,
                        onCopyYesterdayCategory = if (yesterdayCategoryMeals.isNotEmpty()) {
                            { onCopyMealsBatch(yesterdayCategoryMeals, currentDate, "Gårdagens ${catTitle.lowercase()}") }
                        } else null,
                        favoriteFoods = favoriteFoods,
                        isHighlighted = highlightedCategory == catKey,
                        onAddClicked = { selectedCategoryForAdd = catKey },
                        onEditMeal = { mealToEdit = it },
                        onCorrectWithAi = if (aiNutritionService != null) {
                            { mealToCorrectWithAi = it }
                        } else null,
                        onDeleteMeal = { mealToDelete = it },
                        onCopyMeal = { onCopyMeal(it, currentDate) },
                        onToggleFavoriteMeal = { FavoritesManager.toggleFavoriteMeal(context, it) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Bottom Sheet: Choose Add Method (AI Photo vs Manual Search)
    selectedCategoryForAdd?.let { category ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { selectedCategoryForAdd = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Lägg till mat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val cat = category
                            selectedCategoryForAdd = null
                            onAddMealWithAi(cat)
                        },
                    color = EmeraldPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Fotografera mat med AI", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            Text("Ta en bild med kameran eller skanna streckkod", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val cat = category
                            selectedCategoryForAdd = null
                            onAddMealManual(cat)
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Sök i livsmedelsdatabasen", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Sök bland svenska livsmedel eller lägg till ett eget", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (aiNutritionService != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val cat = category
                                selectedCategoryForAdd = null
                                voiceCategoryForSheet = cat
                            }
                            .testTag("diary_add_voice_button"),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Röstlogga med AI", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Säg vad du åt, t.ex. två ägg, rågbröd och kaffe", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // Voice Meal Logging Bottom Sheet from Diary
    voiceCategoryForSheet?.let { voiceCat ->
        val service = aiNutritionService
        if (service != null) {
            VoiceMealLogSheet(
                aiNutritionService = service,
                initialMealType = voiceCat,
                onDismiss = { voiceCategoryForSheet = null },
                onMealAnalyzed = { analysis, chosenMealType ->
                    voiceCategoryForSheet = null
                    if (onVoiceMealAnalyzed != null) {
                        onVoiceMealAnalyzed(analysis, chosenMealType)
                    } else {
                        val newMeal = MealItem(
                            date = currentDate,
                            mealType = chosenMealType,
                            name = analysis.mealName,
                            calories = analysis.totalCalories,
                            protein = analysis.totalProteinG,
                            carbs = analysis.totalCarbsG,
                            fat = analysis.totalFatG,
                            fiber = analysis.totalFiberG,
                            sugar = analysis.totalSugarG,
                            salt = analysis.totalSaltG,
                            itemsJson = FoodIngredient.serializeList(analysis.items),
                            aiAnalyzed = true
                        )
                        onCopyMeal(newMeal, currentDate)
                    }
                }
            )
        }
    }

    // Confirmation Dialog: Delete Meal
    mealToDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { mealToDelete = null },
            title = { Text("Ta bort måltid?") },
            text = { Text("Är du säker på att du vill ta bort '${meal.name}' från matdagboken?") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = meal.id.ifBlank { meal.mealId }
                        onDeleteMeal(id)
                        mealToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ta bort")
                }
            },
            dismissButton = {
                TextButton(onClick = { mealToDelete = null }) {
                    Text("Avbryt")
                }
            }
        )
    }

    // Interactive Portion Size Editor Bottom Sheet for logged meals
    mealToEdit?.let { meal ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val baseIngredients = remember(meal) { meal.getParsedIngredients() }
        val gramsInputs = remember(meal) {
            mutableStateListOf<String>().apply {
                baseIngredients.forEach { add(it.amountGrams.coerceAtLeast(1).toString()) }
            }
        }
        var selectedMultiplier by remember(meal) { mutableDoubleStateOf(1.0) }
        var customPercentText by remember(meal) { mutableStateOf("100") }

        val updatedIngredients: List<FoodIngredient> = remember(baseIngredients, gramsInputs.toList()) {
            baseIngredients.mapIndexed { idx, base ->
                val targetGrams = gramsInputs.getOrNull(idx)?.toIntOrNull()?.coerceAtLeast(1) ?: base.amountGrams
                val factor = targetGrams.toDouble() / base.amountGrams.coerceAtLeast(1).toDouble()
                base.copy(
                    amountGrams = targetGrams,
                    calories = (base.calories * factor).roundToInt(),
                    protein = ((base.protein * factor) * 10.0).roundToInt() / 10.0,
                    carbs = ((base.carbs * factor) * 10.0).roundToInt() / 10.0,
                    fat = ((base.fat * factor) * 10.0).roundToInt() / 10.0,
                    fiber = ((base.fiber * factor) * 10.0).roundToInt() / 10.0,
                    sugar = ((base.sugar * factor) * 10.0).roundToInt() / 10.0,
                    salt = ((base.salt * factor) * 100.0).roundToInt() / 100.0,
                    isEstimated = false
                )
            }
        }

        val fallbackFactor = (customPercentText.replace(',', '.').toDoubleOrNull()?.coerceIn(10.0, 1000.0) ?: (selectedMultiplier * 100.0)) / 100.0

        val previewCalories = if (baseIngredients.isNotEmpty()) {
            updatedIngredients.sumOf { it.calories }
        } else {
            (meal.calories * fallbackFactor).roundToInt()
        }
        val previewProtein = if (baseIngredients.isNotEmpty()) {
            (updatedIngredients.sumOf { it.protein } * 10.0).roundToInt() / 10.0
        } else {
            ((meal.protein * fallbackFactor) * 10.0).roundToInt() / 10.0
        }
        val previewCarbs = if (baseIngredients.isNotEmpty()) {
            (updatedIngredients.sumOf { it.carbs } * 10.0).roundToInt() / 10.0
        } else {
            ((meal.carbs * fallbackFactor) * 10.0).roundToInt() / 10.0
        }
        val previewFat = if (baseIngredients.isNotEmpty()) {
            (updatedIngredients.sumOf { it.fat } * 10.0).roundToInt() / 10.0
        } else {
            ((meal.fat * fallbackFactor) * 10.0).roundToInt() / 10.0
        }
        val previewFiber = if (baseIngredients.isNotEmpty()) {
            (updatedIngredients.sumOf { it.fiber } * 10.0).roundToInt() / 10.0
        } else {
            ((meal.fiber * fallbackFactor) * 10.0).roundToInt() / 10.0
        }
        val previewSugar = if (baseIngredients.isNotEmpty()) {
            (updatedIngredients.sumOf { it.sugar } * 10.0).roundToInt() / 10.0
        } else {
            ((meal.sugar * fallbackFactor) * 10.0).roundToInt() / 10.0
        }
        val previewSalt = if (baseIngredients.isNotEmpty()) {
            (updatedIngredients.sumOf { it.salt } * 100.0).roundToInt() / 100.0
        } else {
            ((meal.salt * fallbackFactor) * 100.0).roundToInt() / 100.0
        }

        ModalBottomSheet(
            onDismissRequest = { mealToEdit = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ändra portionsstorlek",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = meal.name,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { mealToEdit = null }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Stäng")
                    }
                }

                // Quick Multiplier Chips (0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x)
                Text(
                    text = "Snabbjustera portion:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { mult ->
                        val isSelected = selectedMultiplier == mult
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) EmeraldDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedMultiplier = mult
                                    customPercentText = (mult * 100.0).roundToInt().toString()
                                    baseIngredients.forEachIndexed { idx, ing ->
                                        val scaledGrams = (ing.amountGrams * mult).roundToInt().coerceAtLeast(1)
                                        if (idx < gramsInputs.size) {
                                            gramsInputs[idx] = scaledGrams.toString()
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = "${if (mult == mult.toInt().toDouble()) mult.toInt() else mult}x",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                if (baseIngredients.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        baseIngredients.forEachIndexed { idx, baseIng ->
                            val currentText = gramsInputs.getOrElse(idx) { baseIng.amountGrams.toString() }
                            val previewIng = updatedIngredients.getOrNull(idx) ?: baseIng

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = baseIng.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${previewIng.calories} kcal",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = currentText,
                                        onValueChange = { newVal ->
                                            if (idx < gramsInputs.size) {
                                                gramsInputs[idx] = newVal.filter { it.isDigit() }
                                            }
                                        },
                                        label = { Text("Mängd i gram") },
                                        trailingIcon = { Text("g", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edit_meal_grams_input_$idx")
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customPercentText,
                        onValueChange = { customPercentText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Portionsstorlek i procent (100 % = ursprunglig)") },
                        trailingIcon = { Text("%", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_meal_percent_input")
                    )
                }

                // Live Recalculated Nutrition Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Nytt näringsvärde:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$previewCalories kcal",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("P: ${formatMacroDecimal(previewProtein)}g", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MacroProtein)
                            Text("K: ${formatMacroDecimal(previewCarbs)}g", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MacroCarbs)
                            Text("F: ${formatMacroDecimal(previewFat)}g", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MacroFat)
                        }
                    }
                }

                Button(
                    onClick = {
                        val updatedMeal = meal.copy(
                            calories = previewCalories.coerceIn(0, 10000),
                            protein = previewProtein.coerceAtLeast(0.0),
                            carbs = previewCarbs.coerceAtLeast(0.0),
                            fat = previewFat.coerceAtLeast(0.0),
                            fiber = previewFiber.coerceAtLeast(0.0),
                            sugar = previewSugar.coerceAtLeast(0.0),
                            salt = previewSalt.coerceAtLeast(0.0),
                            itemsJson = if (baseIngredients.isNotEmpty()) {
                                FoodIngredient.serializeList(updatedIngredients)
                            } else {
                                meal.itemsJson
                            }
                        )
                        onUpdateMeal(updatedMeal)
                        mealToEdit = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_edit_meal_portion_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Spara ny portion ($previewCalories kcal)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (aiNutritionService != null) {
                    OutlinedButton(
                        onClick = {
                            mealToEdit = null
                            mealToCorrectWithAi = meal
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("edit_sheet_correct_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fel mat eller innehåll? Berätta för AI:n",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // AI Correction & Learning Bottom Sheet for logged meals
    mealToCorrectWithAi?.let { targetMeal ->
        val service = aiNutritionService
        if (service != null) {
            AiCorrectionSheet(
                originalMealName = targetMeal.name,
                currentCalories = targetMeal.calories,
                currentIngredients = targetMeal.getParsedIngredients(),
                imageBitmap = null,
                learnedCorrections = learnedCorrections,
                aiNutritionService = service,
                onDismiss = { mealToCorrectWithAi = null },
                onApplyCorrection = { correctionResult, learnedCorrection ->
                    val corrected = correctionResult.correctedAnalysis
                    val updatedMeal = targetMeal.copy(
                        name = corrected.mealName,
                        calories = corrected.totalCalories.coerceIn(0, 10000),
                        protein = corrected.totalProteinG.coerceAtLeast(0.0),
                        carbs = corrected.totalCarbsG.coerceAtLeast(0.0),
                        fat = corrected.totalFatG.coerceAtLeast(0.0),
                        fiber = corrected.totalFiberG.coerceAtLeast(0.0),
                        sugar = corrected.totalSugarG.coerceAtLeast(0.0),
                        salt = corrected.totalSaltG.coerceAtLeast(0.0),
                        itemsJson = FoodIngredient.serializeList(corrected.items),
                        aiAnalyzed = true
                    )
                    onUpdateMeal(updatedMeal)
                    if (learnedCorrection != null) {
                        onCorrectionLearned(learnedCorrection)
                    }
                    mealToCorrectWithAi = null
                },
                onDeleteLearnedRule = onDeleteLearnedRule
            )
        }
    }
}

@Composable
private fun DiaryMacroSummaryPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun MealCategoryDiarySection(
    title: String,
    categoryKey: String,
    icon: ImageVector,
    accentColor: Color,
    calories: Int,
    meals: List<MealItem>,
    yesterdayCategoryMeals: List<MealItem> = emptyList(),
    onCopyYesterdayCategory: (() -> Unit)? = null,
    favoriteFoods: List<com.example.data.model.SwedishFoodEntry> = emptyList(),
    isHighlighted: Boolean = false,
    onAddClicked: () -> Unit,
    onEditMeal: (MealItem) -> Unit,
    onCorrectWithAi: ((MealItem) -> Unit)? = null,
    onDeleteMeal: (MealItem) -> Unit,
    onCopyMeal: (MealItem) -> Unit,
    onToggleFavoriteMeal: (MealItem) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                EmeraldPrimary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isHighlighted) 2.dp else 1.dp,
            color = if (isHighlighted) EmeraldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$calories kcal",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (calories > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onAddClicked,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("add_meal_to_${categoryKey}_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Lägg till mat i $title",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (meals.isEmpty() && yesterdayCategoryMeals.isNotEmpty() && onCopyYesterdayCategory != null) {
                val yesterdayKcal = yesterdayCategoryMeals.sumOf { it.calories }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCopyYesterdayCategory,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("copy_yesterday_${categoryKey}_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kopiera gårdagens ${title.lowercase()} ($yesterdayKcal kcal)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (meals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    meals.forEach { meal ->
                        val isFav = favoriteFoods.any {
                            FavoritesManager.normalizeFoodName(it.displayName) == FavoritesManager.normalizeFoodName(meal.name)
                        }
                        MealEntryRow(
                            meal = meal,
                            isFavorite = isFav,
                            onEdit = { onEditMeal(meal) },
                            onCorrectWithAi = if (onCorrectWithAi != null) {
                                { onCorrectWithAi(meal) }
                            } else null,
                            onDelete = { onDeleteMeal(meal) },
                            onCopy = { onCopyMeal(meal) },
                            onToggleFavorite = { onToggleFavoriteMeal(meal) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MealEntryRow(
    meal: MealItem,
    isFavorite: Boolean = false,
    onEdit: () -> Unit,
    onCorrectWithAi: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (meal.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = meal.imageUrl,
                    contentDescription = meal.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = meal.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (meal.aiAnalyzed) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(EmeraldPrimary.copy(alpha = 0.16f))
                                .let { mod ->
                                    if (onCorrectWithAi != null) {
                                        mod.clickable { onCorrectWithAi() }
                                    } else mod
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("AI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${meal.calories} kcal",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MacroMiniChip("P ${formatMacroDecimal(meal.protein)}g", MacroProtein)
                    MacroMiniChip("K ${formatMacroDecimal(meal.carbs)}g", MacroCarbs)
                    MacroMiniChip("F ${formatMacroDecimal(meal.fat)}g", MacroFat)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Ändra portionsstorlek",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Alternativ",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (onCorrectWithAi != null) {
                            DropdownMenuItem(
                                text = { Text("Rätta med AI & lär") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onCorrectWithAi()
                                },
                                modifier = Modifier.testTag("diary_correct_ai_button")
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Ändra portion") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) "Ta bort från favoriter" else "Spara som favorit") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleFavorite()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Kopiera måltid") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ta bort", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroMiniChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}
