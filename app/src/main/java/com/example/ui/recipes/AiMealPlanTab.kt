package com.example.ui.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiDailyMealPlan
import com.example.data.model.MealItem
import com.example.data.model.PlannedMeal
import com.example.data.model.UserProfile
import com.example.data.repository.AiNutritionService
import com.example.ui.theme.CalorieOver
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MacroCarbs
import com.example.ui.theme.MacroFat
import com.example.ui.theme.MacroProtein
import com.example.ui.util.formatMacroDecimal
import kotlinx.coroutines.launch

private val DIETARY_PREFERENCES = listOf(
    "Högprotein",
    "Vegetariskt",
    "Veganskt",
    "Lågkolhydrat",
    "Glutenfritt",
    "Laktosfritt",
    "Snabblagat",
    "Budgetvänligt",
    "Husmanskost"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiMealPlanTab(
    currentDate: String,
    effectiveCalorieGoal: Int,
    userProfile: UserProfile?,
    aiNutritionService: AiNutritionService,
    onLogFullMealPlan: (AiDailyMealPlan) -> Unit,
    onLogSingleMeal: (MealItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var caloriesInput by remember(effectiveCalorieGoal) {
        mutableStateOf(effectiveCalorieGoal.coerceIn(1000, 6000).toString())
    }
    var mealsPerDay by remember { mutableIntStateOf(4) }
    var selectedPreferences by remember { mutableStateOf(setOf("Högprotein")) }
    var customNotes by remember { mutableStateOf("") }

    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedPlan by remember { mutableStateOf<AiDailyMealPlan?>(null) }
    var fullPlanLogged by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(EmeraldPrimary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AI-kostschemagenerator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Anpassat svenskt dagsschema med Gemini AI",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = caloriesInput,
                        onValueChange = { input ->
                            caloriesInput = input.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("Dagligt kalorimål (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meal_plan_calories_input")
                    )

                    Column {
                        Text(
                            text = "Antal måltider per dag",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 4, 5, 6).forEach { count ->
                                FilterChip(
                                    selected = mealsPerDay == count,
                                    onClick = { mealsPerDay = count },
                                    label = { Text("$count måltider", fontSize = 12.sp, maxLines = 1, softWrap = false) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldPrimary,
                                        selectedLabelColor = Color.Black
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = "Kostpreferenser",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DIETARY_PREFERENCES.forEach { pref ->
                                val isSelected = selectedPreferences.contains(pref)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedPreferences = if (isSelected) {
                                            selectedPreferences - pref
                                        } else {
                                            selectedPreferences + pref
                                        }
                                    },
                                    label = { Text(pref, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldPrimary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customNotes,
                        onValueChange = { customNotes = it },
                        label = { Text("Extra önskemål (t.ex. inga nötter, gillar lax)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val parsedCalories = caloriesInput.toIntOrNull()?.coerceIn(1000, 6000)
                    Button(
                        onClick = {
                            if (parsedCalories != null && !isGenerating) {
                                isGenerating = true
                                errorMessage = null
                                fullPlanLogged = false
                                coroutineScope.launch {
                                    val result = aiNutritionService.generateDailyMealPlan(
                                        dailyCalories = parsedCalories,
                                        mealsPerDay = mealsPerDay,
                                        preferences = selectedPreferences.toList(),
                                        customNotes = customNotes,
                                        userProfile = userProfile
                                    )
                                    result.onSuccess { plan ->
                                        generatedPlan = plan
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage
                                            ?: "Kunde inte generera kostschema. Kontrollera nätverk och försök igen."
                                    }
                                    isGenerating = false
                                }
                            }
                        },
                        enabled = parsedCalories != null && !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_meal_plan_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Gemini skapar ditt dagsschema...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generera kostschema med AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CalorieOver.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, CalorieOver.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = CalorieOver
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        val plan = generatedPlan
        if (plan != null) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = plan.planTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (plan.summary.isNotBlank()) {
                            Text(
                                text = plan.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MacroMiniStat("Totalt", "${plan.totalCalories} kcal", MaterialTheme.colorScheme.primary)
                            MacroMiniStat("Protein", "${formatMacroDecimal(plan.totalProteinG)}g", MacroProtein)
                            MacroMiniStat("Kolh.", "${formatMacroDecimal(plan.totalCarbsG)}g", MacroCarbs)
                            MacroMiniStat("Fett", "${formatMacroDecimal(plan.totalFatG)}g", MacroFat)
                        }

                        Button(
                            onClick = {
                                if (!fullPlanLogged) {
                                    fullPlanLogged = true
                                    onLogFullMealPlan(plan)
                                }
                            },
                            enabled = !fullPlanLogged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("log_full_meal_plan_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = if (fullPlanLogged) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (fullPlanLogged) {
                                    "Dagsschemat loggat!"
                                } else {
                                    "Logga hela dagsschemat (${plan.meals.size} måltider)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val context = LocalContext.current
                        OutlinedButton(
                            onClick = {
                                val shareText = buildString {
                                    appendLine("📅 AI-genererat kostschema: ${plan.planTitle}")
                                    appendLine("Totalt: ${plan.totalCalories} kcal (P: ${plan.totalProteinG}g, K: ${plan.totalCarbsG}g, F: ${plan.totalFatG}g)")
                                    plan.meals.forEach { meal ->
                                        appendLine("• ${meal.mealType.uppercase()}: ${meal.dishName} (${meal.calories} kcal)")
                                    }
                                    appendLine("Dela detta kostschema via Zevra!")
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Dela kostschema"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Dela kostschema / Exportera", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            items(plan.meals) { meal ->
                PlannedMealCard(
                    meal = meal,
                    onLogSingleMeal = {
                        onLogSingleMeal(meal.toMealItem(currentDate))
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlannedMealCard(
    meal: PlannedMeal,
    onLogSingleMeal: () -> Unit
) {
    var loggedIndividually by remember(meal) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldPrimary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = meal.mealLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "${meal.calories} kcal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RestaurantMenu,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = meal.dishName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (meal.description.isNotBlank()) {
                Text(
                    text = meal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "P: ${formatMacroDecimal(meal.proteinG)}g • K: ${formatMacroDecimal(meal.carbsG)}g • F: ${formatMacroDecimal(meal.fatG)}g",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (meal.ingredients.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    meal.ingredients.forEach { ing ->
                        Text(
                            text = "• ${ing.name}: ${ing.amountGrams} g (${ing.calories} kcal)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    if (!loggedIndividually) {
                        loggedIndividually = true
                        onLogSingleMeal()
                    }
                },
                enabled = !loggedIndividually,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (loggedIndividually) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (loggedIndividually) "Måltid loggad" else "Logga bara denna måltid",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
