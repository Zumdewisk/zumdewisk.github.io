package com.example.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.BuildConfig
import com.example.data.model.AiFoodCorrection
import com.example.data.repository.AiNutritionService
import com.example.ui.diagnostics.AiDiagnosticsDialog
import com.example.update.AppUpdateManager
import com.example.update.AppUpdateState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.repository.NutritionRepository
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MacroCarbs
import com.example.ui.theme.MacroFat
import com.example.ui.theme.MacroProtein
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentProfile: UserProfile?,
    onSaveProfile: (UserProfile) -> Unit,
    onSignOut: () -> Unit,
    aiNutritionService: AiNutritionService? = null,
    learnedCorrections: List<AiFoodCorrection> = emptyList(),
    onDeleteLearnedRule: (String) -> Unit = {},
    appUpdateManager: AppUpdateManager? = null
) {
    val authUser = Firebase.auth.currentUser
    var showAiMemoryDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    var ageText by remember(currentProfile) { mutableStateOf((currentProfile?.age ?: 28).toString()) }
    var heightText by remember(currentProfile) { mutableStateOf((currentProfile?.heightCm ?: 178).toString()) }
    var weightText by remember(currentProfile) { mutableStateOf((currentProfile?.weightKg ?: 75.0).toString()) }

    var selectedActivity by remember(currentProfile) { mutableStateOf(currentProfile?.activityLevel ?: "moderate") }
    var selectedGoal by remember(currentProfile) { mutableStateOf(currentProfile?.goalType ?: "maintain") }

    var isSaving by remember { mutableStateOf(false) }
    var showSavedSnackbar by remember { mutableStateOf(false) }

    val activityOptions = listOf(
        "sedentary" to "Stillasittande (kontorsjobb, lite motion)",
        "light" to "Lätt aktiv (promenader 1-3 ggr/vecka)",
        "moderate" to "Måttligt aktiv (träning 3-5 ggr/vecka)",
        "active" to "Mycket aktiv (hård träning 6-7 ggr/vecka)",
        "very_active" to "Extremt aktiv (atlet, tungt fysiskt arbete)"
    )

    val goalOptions = listOf(
        "lose_weight" to "Viktminskning & fettförbränning",
        "maintain" to "Balanserad kost & behålla vikt",
        "build_muscle" to "Muskeltillväxt & styrka",
        "gain_weight" to "Hälsosam viktuppgång",
        "eat_healthier" to "Förstå mina matvanor och må bra"
    )

    // Dynamically preview recalculated targets
    val previewTargets = remember(ageText, heightText, weightText, selectedActivity, selectedGoal) {
        val age = ageText.trim().toIntOrNull() ?: 28
        val height = heightText.trim().toIntOrNull() ?: 178
        val weight = weightText.trim().replace(',', '.').toDoubleOrNull() ?: 75.0
        NutritionRepository.calculateNutritionTargets(age, height, weight, selectedActivity, selectedGoal)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil och mål", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Account Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authUser?.displayName ?: currentProfile?.displayName?.ifBlank { "Användare" } ?: "Användare",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = authUser?.email ?: currentProfile?.email ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Realtime Sync Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Molnsynkronisering",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldPrimary.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        text = "All data synkroniseras i realtid",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Måltider, vikt, vattenintag och personliga mål sparas tryggt i molnet kopplat till ditt konto (${authUser?.email ?: "Google-konto"}).",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Body Metrics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Kroppsmått",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ageText,
                                onValueChange = { ageText = it },
                                label = { Text("Ålder") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = { heightText = it },
                                label = { Text("Längd (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { weightText = it },
                                label = { Text("Vikt (kg)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Goals & Activity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Aktivitet och träningsvanor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Activity Level Dropdown
                        var activityExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = activityExpanded,
                            onExpandedChange = { activityExpanded = !activityExpanded }
                        ) {
                            OutlinedTextField(
                                value = activityOptions.find { it.first == selectedActivity }?.second ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Aktivitetsnivå") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = activityExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = activityExpanded,
                                onDismissRequest = { activityExpanded = false }
                            ) {
                                activityOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 13.sp) },
                                        onClick = {
                                            selectedActivity = key
                                            activityExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Goal Dropdown
                        var goalExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = goalExpanded,
                            onExpandedChange = { goalExpanded = !goalExpanded }
                        ) {
                            OutlinedTextField(
                                value = goalOptions.find { it.first == selectedGoal }?.second ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Personligt mål") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = goalExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = goalExpanded,
                                onDismissRequest = { goalExpanded = false }
                            ) {
                                goalOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 13.sp) },
                                        onClick = {
                                            selectedGoal = key
                                            goalExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Calculated Daily Targets Preview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Beräknade dagsmål",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${previewTargets.calories} kcal/dag",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TargetPill(label = "Protein", value = "${previewTargets.proteinG}g", color = MacroProtein, modifier = Modifier.weight(1f))
                            TargetPill(label = "Kolhydr.", value = "${previewTargets.carbsG}g", color = MacroCarbs, modifier = Modifier.weight(1f))
                            TargetPill(label = "Fett", value = "${previewTargets.fatG}g", color = MacroFat, modifier = Modifier.weight(1f))
                            TargetPill(label = "Vatten", value = "${previewTargets.waterMl}ml", color = Color(0xFF06B6D4), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Save Profile Button
            item {
                Button(
                    onClick = {
                        isSaving = true
                        val age = ageText.trim().toIntOrNull() ?: 28
                        val height = heightText.trim().toIntOrNull() ?: 178
                        val weight = weightText.trim().replace(',', '.').toDoubleOrNull() ?: 75.0

                        val updated = (currentProfile ?: UserProfile()).copy(
                            userId = authUser?.uid ?: currentProfile?.userId ?: "",
                            email = authUser?.email ?: currentProfile?.email ?: "",
                            displayName = authUser?.displayName ?: currentProfile?.displayName ?: "",
                            age = age,
                            heightCm = height,
                            weightKg = weight,
                            activityLevel = selectedActivity,
                            goalType = selectedGoal,
                            targetCalories = previewTargets.calories,
                            targetProteinG = previewTargets.proteinG,
                            targetCarbsG = previewTargets.carbsG,
                            targetFatG = previewTargets.fatG,
                            targetWaterMl = previewTargets.waterMl
                        )
                        onSaveProfile(updated)
                        isSaving = false
                        showSavedSnackbar = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_profile_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.Black
                    ),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Spara ändringar och uppdatera mål", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // AI Memory & Learned Rules Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI-minne (${learnedCorrections.size} regler)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Hantera det AI:n har lärt sig från dina rättelser",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { showAiMemoryDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("profile_ai_memory_button")
                        ) {
                            Text("Visa", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // AI- och App Check-diagnostik
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_ai_diagnostics_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI- & App Check-diagnostik",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Testa Gemini-anslutning och debug-tokens",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { showDiagnosticsDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("profile_ai_diagnostics_button")
                        ) {
                            Text("Diagnostik", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Appversion & manuell uppdateringskontroll
            if (appUpdateManager != null) {
                item {
                    val updateState by appUpdateManager.state.collectAsState()
                    val isBusy = updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_app_update_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = EmeraldDark,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Zevra v${BuildConfig.VERSION_NAME}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Byggkod ${BuildConfig.VERSION_CODE} • Uppdatera direkt i appen",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = { appUpdateManager.checkForUpdates(manual = true) },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("check_app_update_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Sök uppdatering", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Sign Out Button
            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logga ut", fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showDiagnosticsDialog) {
            val effectiveService = aiNutritionService ?: remember { AiNutritionService() }
            AiDiagnosticsDialog(
                onDismiss = { showDiagnosticsDialog = false },
                aiNutritionService = effectiveService
            )
        }

        if (showAiMemoryDialog) {
            AlertDialog(
                onDismissRequest = { showAiMemoryDialog = false },
                title = { Text("AI:ns inlärda regler", fontWeight = FontWeight.Bold) },
                text = {
                    if (learnedCorrections.isEmpty()) {
                        Text(
                            text = "AI:n har inga sparade regler än. När du rättar en analyserad måltid sparas din feedback här så att AI:n gör rätt i framtiden.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(learnedCorrections.size) { index ->
                                val corr = learnedCorrections[index]
                                val ruleId = corr.id.ifBlank { corr.correctionId }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = corr.learningRule,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (corr.originalDish.isNotBlank() && corr.correctedDish.isNotBlank()) {
                                                Text(
                                                    text = "${corr.originalDish} → ${corr.correctedDish}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        if (ruleId.isNotBlank()) {
                                            IconButton(
                                                onClick = { onDeleteLearnedRule(ruleId) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Ta bort regel",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAiMemoryDialog = false }) {
                        Text("Stäng")
                    }
                }
            )
        }
    }
}

@Composable
private fun TargetPill(
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
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        }
    }
}
