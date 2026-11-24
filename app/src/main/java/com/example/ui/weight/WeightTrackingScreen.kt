package com.example.ui.weight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.model.WeightLog
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTrackingScreen(
    userProfile: UserProfile?,
    weightLogs: List<WeightLog>,
    onLogWeight: (weightKg: Double, date: String, note: String) -> Unit,
    onDeleteLog: (logId: String) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTimeframeIndex by remember { mutableIntStateOf(0) } // 0 = 7 dagar, 1 = 30 dagar, 2 = 90 dagar, 3 = Alla
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val todayStr = remember(today) { today.format(dateFormatter) }

    // Filter logs according to selected timeframe
    val filteredLogs = remember(weightLogs, selectedTimeframeIndex) {
        val sorted = weightLogs.sortedBy { it.date }
        when (selectedTimeframeIndex) {
            0 -> {
                val cutoff = today.minusDays(7).format(dateFormatter)
                sorted.filter { it.date >= cutoff }
            }
            1 -> {
                val cutoff = today.minusDays(30).format(dateFormatter)
                sorted.filter { it.date >= cutoff }
            }
            2 -> {
                val cutoff = today.minusDays(90).format(dateFormatter)
                sorted.filter { it.date >= cutoff }
            }
            else -> sorted
        }
    }

    // Weight calculations
    val latestLog = weightLogs.maxByOrNull { it.date }
    val currentWeight = latestLog?.weightKg ?: userProfile?.weightKg ?: 75.0
    val firstLogInTimeframe = filteredLogs.firstOrNull()
    val weightChange = if (firstLogInTimeframe != null) {
        currentWeight - firstLogInTimeframe.weightKg
    } else 0.0

    // Height & BMI
    val heightCm = userProfile?.heightCm ?: 178
    val bmi = if (heightCm > 0) {
        val hM = heightCm / 100.0
        currentWeight / (hM * hM)
    } else 0.0

    val bmiCategory = when {
        bmi < 18.5 -> "Undervikt"
        bmi < 25.0 -> "Normalvikt"
        bmi < 30.0 -> "Övervikt"
        else -> "Fetma"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MonitorWeight,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Viktspårning och framsteg", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("weight_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tillbaka")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("log_weight_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logga vikt", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Summary Card (Current weight, BMI, Trend)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "Aktuell vikt",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = com.example.ui.util.formatWeightKg(currentWeight),
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = " kg",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                                if (latestLog != null) {
                                    Text(
                                        text = "Senast loggad: ${com.example.ui.util.formatSwedishDateLabel(latestLog.date)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Trend badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    weightChange < -0.1 -> Color(0xFF10B981).copy(alpha = 0.15f)
                                    weightChange > 0.1 -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val trendIcon = when {
                                        weightChange < -0.1 -> Icons.AutoMirrored.Filled.TrendingDown
                                        weightChange > 0.1 -> Icons.AutoMirrored.Filled.TrendingUp
                                        else -> Icons.AutoMirrored.Filled.TrendingFlat
                                    }
                                    val trendColor = when {
                                        weightChange < -0.1 -> MaterialTheme.colorScheme.primary
                                        weightChange > 0.1 -> Color(0xFFD97706)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Icon(
                                        imageVector = trendIcon,
                                        contentDescription = null,
                                        tint = trendColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (weightChange == 0.0) "0,0 kg" else String.format(Locale.forLanguageTag("sv-SE"), "%+.1f kg", weightChange),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = trendColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Secondary Stats Row (BMI, Goal, Height)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WeightStatPill(
                                label = "BMI",
                                value = String.format(Locale.forLanguageTag("sv-SE"), "%.1f", bmi),
                                subtext = bmiCategory,
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            WeightStatPill(
                                label = "Längd",
                                value = "$heightCm cm",
                                subtext = "Referens",
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            WeightStatPill(
                                label = "Mål",
                                value = when (userProfile?.goalType) {
                                    "lose_weight" -> "Gå ner"
                                    "gain_weight" -> "Gå upp"
                                    "build_muscle" -> "Muskler"
                                    else -> "Behålla"
                                },
                                subtext = "${userProfile?.targetCalories ?: 2200} kcal",
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Timeframe Selector Tabs
            item {
                TabRow(
                    selectedTabIndex = selectedTimeframeIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    val tabs = listOf("7 dagar", "30 dagar", "90 dagar", "Alla")
                    tabs.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTimeframeIndex == index,
                            onClick = {
                                selectedTimeframeIndex = index
                                selectedPointIndex = null
                            },
                            text = { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            modifier = Modifier.testTag("timeframe_tab_$index")
                        )
                    }
                }
            }

            // Interactive Line Chart Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Viktutveckling över tid",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${filteredLogs.size} loggar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (filteredLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.MonitorWeight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Inga viktloggar för denna period ännu.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Tryck på \"Logga vikt\" nedan för att börja följa din kurva.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            WeightProgressLineChart(
                                logs = filteredLogs,
                                selectedPointIndex = selectedPointIndex,
                                onPointSelected = { selectedPointIndex = it }
                            )

                            selectedPointIndex?.let { idx ->
                                if (idx in filteredLogs.indices) {
                                    val log = filteredLogs[idx]
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = EmeraldPrimary.copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(text = com.example.ui.util.formatSwedishDateLabel(log.date), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                if (log.note.isNotBlank()) {
                                                    Text(text = "Anteckning: ${log.note}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Text(
                                                text = "${com.example.ui.util.formatWeightKg(log.weightKg)} kg",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 17.sp,
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

            // History Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Logghistorik",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${weightLogs.size} registreringar",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // History List
            if (weightLogs.isEmpty()) {
                item {
                    Text(
                        text = "Ingen historik finns än. Logga din första vikt för att komma igång!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(weightLogs.sortedByDescending { it.date }) { log ->
                    WeightHistoryItem(
                        log = log,
                        onDelete = { onDeleteLog(log.logId.ifBlank { log.id }) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
            }
        }
    }

    // Modal Sheet for Logging Weight
    if (showAddDialog) {
        AddWeightBottomSheet(
            initialWeight = currentWeight,
            initialDate = todayStr,
            onDismiss = { showAddDialog = false },
            onConfirm = { weight, date, note ->
                onLogWeight(weight, date, note)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun WeightProgressLineChart(
    logs: List<WeightLog>,
    selectedPointIndex: Int?,
    onPointSelected: (Int?) -> Unit
) {
    val points = remember(logs) { logs.sortedBy { it.date } }
    if (points.isEmpty()) return

    val weights = remember(points) { points.map { it.weightKg } }
    val minWeight = remember(weights) { (weights.minOrNull() ?: 70.0) - 1.5 }
    val maxWeight = remember(weights) { (weights.maxOrNull() ?: 80.0) + 1.5 }
    val weightRange = remember(minWeight, maxWeight) { (maxWeight - minWeight).coerceAtLeast(1.0) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Max ${com.example.ui.util.formatWeightKg(weights.maxOrNull() ?: 0.0)} kg",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Min ${com.example.ui.util.formatWeightKg(weights.minOrNull() ?: 0.0)} kg",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(points) {
                    detectTapGestures(
                        onTap = { offset ->
                            val width = size.width
                            val step = if (points.size > 1) width / (points.size - 1) else width
                            val tappedIndex = ((offset.x + step / 2) / step).toInt().coerceIn(0, points.size - 1)
                            onPointSelected(tappedIndex)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val topPadding = 16.dp.toPx()
                val bottomPadding = 24.dp.toPx()
                val usableHeight = h - topPadding - bottomPadding

                val gridLinesCount = 4
                for (i in 0..gridLinesCount) {
                    val y = topPadding + (usableHeight / gridLinesCount) * i
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.25f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                if (points.size == 1) {
                    val p = points[0]
                    val y = topPadding + usableHeight - (((p.weightKg - minWeight) / weightRange) * usableHeight).toFloat()
                    val x = w / 2

                    drawCircle(
                        color = EmeraldPrimary,
                        radius = 8.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                    return@Canvas
                }

                val stepX = w / (points.size - 1)
                val coords = points.mapIndexed { index, log ->
                    val x = index * stepX
                    val normY = ((log.weightKg - minWeight) / weightRange).toFloat()
                    val y = topPadding + usableHeight - (normY * usableHeight)
                    Offset(x, y)
                }

                val strokePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 0 until coords.size - 1) {
                        val p0 = coords[i]
                        val p1 = coords[i + 1]
                        val cx = (p0.x + p1.x) / 2
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(coords.last().x, h - bottomPadding)
                    lineTo(coords.first().x, h - bottomPadding)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            EmeraldPrimary.copy(alpha = 0.35f),
                            EmeraldPrimary.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = topPadding,
                        endY = h - bottomPadding
                    )
                )

                drawPath(
                    path = strokePath,
                    color = EmeraldPrimary,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                coords.forEachIndexed { idx, pt ->
                    val isSelected = selectedPointIndex == idx
                    val radius = if (isSelected) 7.dp.toPx() else 4.dp.toPx()
                    val outerRadius = if (isSelected) 10.dp.toPx() else 6.dp.toPx()

                    drawCircle(
                        color = if (isSelected) EmeraldDark else EmeraldPrimary.copy(alpha = 0.3f),
                        radius = outerRadius,
                        center = pt
                    )
                    drawCircle(
                        color = if (isSelected) EmeraldPrimary else Color.White,
                        radius = radius,
                        center = pt
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val firstDate = points.first().date.takeLast(5)
            val middleDate = points[points.size / 2].date.takeLast(5)
            val lastDate = points.last().date.takeLast(5)

            Text(text = firstDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (points.size > 2) {
                Text(text = middleDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = lastDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeightStatPill(
    label: String,
    value: String,
    subtext: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(text = subtext, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeightHistoryItem(
    log: WeightLog,
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
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(text = com.example.ui.util.formatSwedishDateLabel(log.date), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (log.note.isNotBlank()) {
                        Text(
                            text = log.note,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${com.example.ui.util.formatWeightKg(log.weightKg)} kg",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Radera logg",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWeightBottomSheet(
    initialWeight: Double,
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (weight: Double, date: String, note: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var weightInput by remember { mutableStateOf(String.format(Locale.US, "%.1f", initialWeight)) }
    var dateInput by remember { mutableStateOf(initialDate) }
    var noteInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Logga dagens vikt",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldPrimary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "kg",
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDark,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }
            }

            // Weight numeric input
            OutlinedTextField(
                value = weightInput,
                onValueChange = {
                    weightInput = it
                    errorMessage = null
                },
                label = { Text("Vikt (i kg, t.ex. 74.5)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weight_input_field")
            )

            // Date input (default to today)
            OutlinedTextField(
                value = dateInput,
                onValueChange = {
                    dateInput = it
                    errorMessage = null
                },
                label = { Text("Datum (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weight_date_field")
            )

            // Optional Note
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("Anteckning (valfritt, t.ex. Morgonvikt fastande)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weight_note_field")
            )

            errorMessage?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val parsed = weightInput.trim().replace(',', '.').toDoubleOrNull()
                    if (parsed == null || parsed < 20.0 || parsed > 350.0) {
                        errorMessage = "Ange en giltig vikt mellan 20 och 350 kg."
                        return@Button
                    }
                    if (!dateInput.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                        errorMessage = "Ange datum i formatet YYYY-MM-DD."
                        return@Button
                    }
                    onConfirm(parsed, dateInput.trim(), noteInput.trim())
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_weight_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Spara viktmätning", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
