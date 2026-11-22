package com.example.ui.scanner

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiFoodCorrection
import com.example.data.model.FoodIngredient
import com.example.data.model.MealItem
import com.example.data.model.SwedishFoodDatabase
import com.example.data.repository.AiMealAnalysisResult
import com.example.data.repository.AiNutritionService
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MacroCarbs
import com.example.ui.theme.MacroFat
import com.example.ui.theme.MacroProtein

import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealAnalysisReviewScreen(
    initialAnalysis: AiMealAnalysisResult,
    defaultMealType: String = "lunch",
    currentDate: String,
    imageUrl: String = "",
    onSaveMeal: (MealItem) -> Unit,
    onCancel: () -> Unit,
    aiNutritionService: AiNutritionService? = null,
    learnedCorrections: List<AiFoodCorrection> = emptyList(),
    onCorrectionLearned: (AiFoodCorrection) -> Unit = {},
    onDeleteLearnedRule: (String) -> Unit = {}
) {
    var mealName by remember { mutableStateOf(initialAnalysis.mealName) }
    var selectedMealType by remember { mutableStateOf(defaultMealType) }
    val ingredients = remember { mutableStateListOf<FoodIngredient>().apply { addAll(initialAnalysis.items) } }

    var editingIngredientIndex by remember { mutableStateOf<Int?>(null) }
    var showAddIngredientDialog by remember { mutableStateOf(false) }
    var showAiCorrectionSheet by remember { mutableStateOf(false) }
    var latestAiReply by remember { mutableStateOf<String?>(null) }
    var latestLearnedRule by remember { mutableStateOf<String?>(null) }
    val displayBitmap = remember { AiNutritionService.lastBitmap }

    // Dynamic totals recalculated from the current ingredients
    val totalCalories = ingredients.sumOf { it.calories }
    val totalProtein = ingredients.sumOf { it.protein }
    val totalCarbs = ingredients.sumOf { it.carbs }
    val totalFat = ingredients.sumOf { it.fat }
    val totalFiber = ingredients.sumOf { it.fiber }
    val totalSugar = ingredients.sumOf { it.sugar }
    val totalSalt = ingredients.sumOf { it.salt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Granska AI-analys", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tillbaka")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            val meal = MealItem(
                                mealId = "",
                                userId = "",
                                date = currentDate,
                                mealType = selectedMealType,
                                name = mealName.ifBlank { "Måltid" },
                                calories = totalCalories,
                                protein = Math.round(totalProtein * 10.0) / 10.0,
                                carbs = Math.round(totalCarbs * 10.0) / 10.0,
                                fat = Math.round(totalFat * 10.0) / 10.0,
                                fiber = Math.round(totalFiber * 10.0) / 10.0,
                                sugar = Math.round(totalSugar * 10.0) / 10.0,
                                salt = Math.round(totalSalt * 100.0) / 100.0,
                                itemsJson = FoodIngredient.serializeList(ingredients),
                                aiAnalyzed = true,
                                imageUrl = imageUrl
                            )
                            onSaveMeal(meal)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_meal_button"),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Spara i matdagboken ($totalCalories kcal)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Food Photo Banner (from URL or local bitmap)
            if (imageUrl.isNotBlank() || displayBitmap != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Fotograferad måltid",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else if (displayBitmap != null) {
                            Image(
                                bitmap = displayBitmap.asImageBitmap(),
                                contentDescription = "Fotograferad måltid",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            // Plate Proportion & Volume Reasoning Card
            if (initialAnalysis.reasoning.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Tallriks- & volymanalys",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = initialAnalysis.reasoning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Meal Name & Meal Type Selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Måltidsnamn",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = mealName,
                            onValueChange = { mealName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Måltidskategori",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val types = listOf(
                                "frukost" to "Frukost",
                                "lunch" to "Lunch",
                                "middag" to "Middag",
                                "mellanmal" to "Mellanmål"
                            )
                            types.forEach { (typeKey, label) ->
                                val selected = selectedMealType == typeKey
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedMealType = typeKey },
                                    label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldPrimary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // AI Confidence Notice (if uncertain)
            if (initialAnalysis.confidence == "medium" || initialAnalysis.confidence == "low" || initialAnalysis.reasoning.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (initialAnalysis.confidence == "low") Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (initialAnalysis.confidence == "low") Icons.Default.Warning else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (initialAnalysis.confidence == "low") Color(0xFFD97706) else EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (initialAnalysis.confidence == "low") "AI:n är osäker på vissa delar" else "AI-notering",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (initialAnalysis.confidence == "low") Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = initialAnalysis.reasoning.ifBlank { "Kontrollera gärna ingredienserna och portionsmängderna nedan." },
                                    fontSize = 12.sp,
                                    color = if (initialAnalysis.confidence == "low") Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Interactive AI Correction & Learning Banner
            if (aiNutritionService != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = EmeraldPrimary.copy(alpha = 0.08f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Blev maten eller vikten fel?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Berätta för AI:n så räknar den om direkt och kommer ihåg det till nästa gång.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            latestAiReply?.let { reply ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldDark.copy(alpha = 0.3f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = reply,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        latestLearnedRule?.takeIf { it.isNotBlank() }?.let { rule ->
                                            Text(
                                                text = "Inlärt minne: $rule",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showAiCorrectionSheet = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("review_correct_ai_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Berätta vad som är fel & lär AI:n",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Nutrition Summary Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Näringsinnehåll för hela måltiden",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NutrientBadge(label = "Kalorier", value = "$totalCalories kcal", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            NutrientBadge(label = "Protein", value = "${kotlin.math.round(totalProtein).toInt()}g", color = MacroProtein, modifier = Modifier.weight(1f))
                            NutrientBadge(label = "Kolhydr.", value = "${kotlin.math.round(totalCarbs).toInt()}g", color = MacroCarbs, modifier = Modifier.weight(1f))
                            NutrientBadge(label = "Fett", value = "${kotlin.math.round(totalFat).toInt()}g", color = MacroFat, modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Fibrer: ${com.example.ui.util.formatMacroDecimal(totalFiber)} g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "Socker: ${com.example.ui.util.formatMacroDecimal(totalSugar)} g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "Salt: ${com.example.ui.util.formatMacroDecimal(totalSalt)} g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Ingredients Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Identifierade livsmedel (${ingredients.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { showAddIngredientDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lägg till", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
            }

            // Ingredients List
            itemsIndexed(ingredients) { index, item ->
                IngredientEditCard(
                    ingredient = item,
                    onEdit = { editingIngredientIndex = index },
                    onDelete = { ingredients.removeAt(index) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Dialog: Edit Ingredient Amount
    editingIngredientIndex?.let { index ->
        val item = ingredients[index]
        var gramsText by remember { mutableStateOf(item.amountGrams.toString()) }

        AlertDialog(
            onDismissRequest = { editingIngredientIndex = null },
            title = { Text("Ändra mängd för ${item.name}") },
            text = {
                Column {
                    Text(
                        text = "Ange ny portionsmängd i gram. Kalorier och makron räknas om proportionellt.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        label = { Text("Gram (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newGrams = gramsText.toIntOrNull()?.coerceAtLeast(1) ?: item.amountGrams
                        val hasDbMatch = SwedishFoodDatabase.findBestMatch(item.name) != null
                        val recalculated = if (!hasDbMatch && item.amountGrams > 0 && item.calories > 0) {
                            val ratio = newGrams.toDouble() / item.amountGrams.toDouble()
                            item.copy(
                                amountGrams = newGrams,
                                calories = kotlin.math.round(item.calories * ratio).toInt(),
                                protein = kotlin.math.round(item.protein * ratio * 10.0) / 10.0,
                                carbs = kotlin.math.round(item.carbs * ratio * 10.0) / 10.0,
                                fat = kotlin.math.round(item.fat * ratio * 10.0) / 10.0,
                                fiber = kotlin.math.round(item.fiber * ratio * 10.0) / 10.0,
                                sugar = kotlin.math.round(item.sugar * ratio * 10.0) / 10.0,
                                salt = kotlin.math.round(item.salt * ratio * 100.0) / 100.0,
                                isEstimated = false
                            )
                        } else {
                            SwedishFoodDatabase.calculateNutritionForFood(
                                name = item.name,
                                weightGrams = newGrams,
                                confidence = item.confidence
                            ).copy(isEstimated = false)
                        }
                        ingredients[index] = recalculated
                        editingIngredientIndex = null
                    }
                ) {
                    Text("Spara")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIngredientIndex = null }) {
                    Text("Avbryt")
                }
            }
        )
    }

    // Dialog: Add Missing Ingredient
    if (showAddIngredientDialog) {
        var query by remember { mutableStateOf("") }
        var customName by remember { mutableStateOf("") }
        var customGrams by remember { mutableStateOf("100") }

        AlertDialog(
            onDismissRequest = { showAddIngredientDialog = false },
            title = { Text("Lägg till ingrediens") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Välj bland vanliga svenska livsmedel eller ange ett eget:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Sök t.ex. dillsås, potatis...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val searchResults = SwedishFoodDatabase.search(query).take(4)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        searchResults.forEach { food ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val ing = food.toIngredient(food.defaultServingGrams)
                                        ingredients.add(ing)
                                        showAddIngredientDialog = false
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(food.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("${food.caloriesPer100g} kcal/100g", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Eller ange manuellt:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        placeholder = { Text("Namn (t.ex. Sås)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customGrams,
                        onValueChange = { customGrams = it },
                        label = { Text("Mängd (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customName.isNotBlank()) {
                            val grams = customGrams.toIntOrNull()?.coerceAtLeast(1) ?: 100
                            val calculated = SwedishFoodDatabase.calculateNutritionForFood(
                                name = customName.trim(),
                                weightGrams = grams,
                                confidence = 1.0
                            )
                            ingredients.add(calculated.copy(isEstimated = false))
                        }
                        showAddIngredientDialog = false
                    }
                ) {
                    Text("Lägg till")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddIngredientDialog = false }) {
                    Text("Stäng")
                }
            }
        )
    }

    if (showAiCorrectionSheet && aiNutritionService != null) {
        AiCorrectionSheet(
            originalMealName = mealName,
            currentCalories = totalCalories,
            currentIngredients = ingredients.toList(),
            imageBitmap = displayBitmap,
            learnedCorrections = learnedCorrections,
            aiNutritionService = aiNutritionService,
            onDismiss = { showAiCorrectionSheet = false },
            onApplyCorrection = { result, learnedCorrection ->
                mealName = result.correctedAnalysis.mealName
                ingredients.clear()
                ingredients.addAll(result.correctedAnalysis.items)
                latestAiReply = result.aiReply
                latestLearnedRule = learnedCorrection?.learningRule
                if (learnedCorrection != null) {
                    onCorrectionLearned(learnedCorrection)
                }
                showAiCorrectionSheet = false
            },
            onDeleteLearnedRule = onDeleteLearnedRule
        )
    }
}

@Composable
private fun IngredientEditCard(
    ingredient: FoodIngredient,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ingredient.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (ingredient.isEstimated) "~${ingredient.amountGrams}g" else "${ingredient.amountGrams}g",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldPrimary.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (ingredient.isEstimated) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Uppskattad portion (${(ingredient.confidence * 100).toInt()} % säkerhet) • Tryck på pennan för att ändra",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${ingredient.calories} kcal • P: ${com.example.ui.util.formatMacroDecimal(ingredient.protein)}g • K: ${com.example.ui.util.formatMacroDecimal(ingredient.carbs)}g • F: ${com.example.ui.util.formatMacroDecimal(ingredient.fat)}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Ändra mängd",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Ta bort",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NutrientBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        }
    }
}
