package com.example.ui.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiDailyMealPlan
import com.example.data.model.MealItem
import com.example.data.model.Recipe
import com.example.data.model.UserProfile
import com.example.data.repository.AiNutritionService
import com.example.ui.theme.CalorieOver
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MacroCarbs
import com.example.ui.theme.MacroFat
import com.example.ui.theme.MacroFiber
import com.example.ui.theme.MacroProtein
import com.example.ui.util.formatMacroDecimal
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesAndMealPlanScreen(
    currentDate: String,
    formattedDateDisplay: String,
    effectiveCalorieGoal: Int,
    userProfile: UserProfile?,
    recipes: List<Recipe>,
    aiNutritionService: AiNutritionService,
    onSaveRecipe: (Recipe) -> Unit,
    onDeleteRecipe: (String) -> Unit,
    onLogSingleMeal: (MealItem) -> Unit,
    onLogFullMealPlan: (AiDailyMealPlan) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Mina recept, 1 = AI-kostschema
    var showRecipeBuilder by remember { mutableStateOf(false) }
    var recipeBeingEdited by remember { mutableStateOf<Recipe?>(null) }
    var recipeToLog by remember { mutableStateOf<Recipe?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recept & Kostschema", fontWeight = FontWeight.Bold)
                        Text(
                            text = formattedDateDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("recipes_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tillbaka"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mina recept (${recipes.size})", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    },
                    modifier = Modifier.testTag("tab_my_recipes")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI-kostschema", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    },
                    modifier = Modifier.testTag("tab_ai_meal_plan")
                )
            }

            when (selectedTab) {
                0 -> MyRecipesTab(
                    recipes = recipes,
                    onCreateNew = {
                        recipeBeingEdited = null
                        showRecipeBuilder = true
                    },
                    onEditRecipe = { recipe ->
                        recipeBeingEdited = recipe
                        showRecipeBuilder = true
                    },
                    onLogRecipe = { recipe ->
                        recipeToLog = recipe
                    },
                    onDeleteRecipe = { recipeId ->
                        onDeleteRecipe(recipeId)
                    }
                )
                1 -> AiMealPlanTab(
                    currentDate = currentDate,
                    effectiveCalorieGoal = effectiveCalorieGoal,
                    userProfile = userProfile,
                    aiNutritionService = aiNutritionService,
                    onLogFullMealPlan = onLogFullMealPlan,
                    onLogSingleMeal = onLogSingleMeal
                )
            }
        }
    }

    if (showRecipeBuilder) {
        RecipeBuilderSheet(
            existingRecipe = recipeBeingEdited,
            aiNutritionService = aiNutritionService,
            onDismiss = {
                showRecipeBuilder = false
                recipeBeingEdited = null
            },
            onSaveRecipe = { recipe ->
                onSaveRecipe(recipe)
                showRecipeBuilder = false
                recipeBeingEdited = null
            }
        )
    }

    val logTarget = recipeToLog
    if (logTarget != null) {
        LogRecipePortionDialog(
            recipe = logTarget,
            currentDate = currentDate,
            onDismiss = { recipeToLog = null },
            onConfirm = { mealItem ->
                onLogSingleMeal(mealItem)
                recipeToLog = null
            }
        )
    }
}

@Composable
private fun MyRecipesTab(
    recipes: List<Recipe>,
    onCreateNew: () -> Unit,
    onEditRecipe: (Recipe) -> Unit,
    onLogRecipe: (Recipe) -> Unit,
    onDeleteRecipe: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onCreateNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("create_recipe_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Skapa nytt recept", fontWeight = FontWeight.Bold)
            }
        }

        if (recipes.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Inga sparade recept ännu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bygg egna favoriträtter av svenska livsmedel, ange antal portioner och få kalorier samt makron per portion automatiskt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recipes, key = { it.recipeId.ifBlank { it.id } }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onLogPortion = { onLogRecipe(recipe) },
                    onEdit = { onEditRecipe(recipe) },
                    onDelete = { onDeleteRecipe(recipe.recipeId.ifBlank { recipe.id }) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    onLogPortion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val ingredients = remember(recipe.itemsJson) { recipe.ingredients() }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${recipe.servings} portioner • ${ingredients.size} ingredienser",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            val shareText = buildString {
                                appendLine("🍽️ Recept: ${recipe.name}")
                                appendLine("Portioner: ${recipe.servings}")
                                appendLine("Kalorier per portion: ${recipe.perServingCalories()} kcal")
                                appendLine("Protein: ${formatMacroDecimal(recipe.perServingProtein())}g | Kolh: ${formatMacroDecimal(recipe.perServingCarbs())}g | Fett: ${formatMacroDecimal(recipe.perServingFat())}g")
                                appendLine("Ingredienser:")
                                ingredients.forEach { ing ->
                                    appendLine("  - ${ing.name}: ${ing.amountGrams}g (${ing.calories} kcal)")
                                }
                                appendLine("Dela detta recept via Zevra!")
                            }
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Dela recept"))
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Dela ${recipe.name}",
                            tint = EmeraldPrimary
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Redigera ${recipe.name}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Ta bort ${recipe.name}",
                            tint = CalorieOver
                        )
                    }
                }
            }

            if (recipe.description.isNotBlank()) {
                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MacroMiniStat("Per port", "${recipe.perServingCalories()} kcal", MaterialTheme.colorScheme.primary)
                    MacroMiniStat("Protein", "${formatMacroDecimal(recipe.perServingProtein())}g", MacroProtein)
                    MacroMiniStat("Kolh.", "${formatMacroDecimal(recipe.perServingCarbs())}g", MacroCarbs)
                    MacroMiniStat("Fett", "${formatMacroDecimal(recipe.perServingFat())}g", MacroFat)
                    MacroMiniStat("Fiber", "${formatMacroDecimal(recipe.perServingFiber())}g", MacroFiber)
                }
            }

            if (ingredients.isNotEmpty()) {
                Text(
                    text = ingredients.take(4).joinToString(", ") { "${it.name} (${it.amountGrams}g)" } +
                        if (ingredients.size > 4) " + ${ingredients.size - 4} till" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onLogPortion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Logga portion i dagboken", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LogRecipePortionDialog(
    recipe: Recipe,
    currentDate: String,
    onDismiss: () -> Unit,
    onConfirm: (MealItem) -> Unit
) {
    var selectedMealType by remember { mutableStateOf("lunch") }
    var portionsEaten by remember { mutableDoubleStateOf(1.0) }

    val previewKcal = (recipe.perServingCalories() * portionsEaten).roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logga ${recipe.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Välj måltidskategori",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "frukost" to "Frukost",
                        "lunch" to "Lunch",
                        "middag" to "Middag",
                        "mellanmal" to "Mellanmål"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedMealType == key,
                            onClick = { selectedMealType = key },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Text(
                    text = "Antal portioner ($previewKcal kcal)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.5, 1.0, 1.5, 2.0).forEach { qty ->
                        FilterChip(
                            selected = portionsEaten == qty,
                            onClick = { portionsEaten = qty },
                            label = { Text("${qty.toString().replace('.', ',')} port", maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(recipe.toMealItem(currentDate, selectedMealType, portionsEaten))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text("Logga ($previewKcal kcal)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        }
    )
}
