package com.example.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.repository.AiMealAnalysisResult
import com.example.data.repository.AiNutritionService
import com.example.ui.theme.EmeraldPrimary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMealLogSheet(
    aiNutritionService: AiNutritionService,
    initialMealType: String = "lunch",
    onDismiss: () -> Unit,
    onMealAnalyzed: (AiMealAnalysisResult, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedMealType by remember { mutableStateOf(initialMealType) }
    var transcript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                SpeechRecognizer.createSpeechRecognizer(context)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        isListening = false
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                transcript = if (transcript.isBlank()) spoken else "${transcript.trim()} $spoken"
                statusError = null
            }
        }
    }

    fun buildSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sv-SE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "sv-SE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Säg vad du åt, t.ex. två stekta ägg och rågbröd...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    fun startSpeechCapture() {
        statusError = null
        val recognizer = speechRecognizer
        if (recognizer == null) {
            try {
                systemSpeechLauncher.launch(buildSpeechIntent())
            } catch (_: Exception) {
                statusError = "Röstinmatning via mikrofon saknas på denna enhet. Skriv eller välj ett exempel nedan."
            }
            return
        }

        val prefix = transcript.trim()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                statusError = null
            }

            override fun onBeginningOfSpeech() {
                isListening = true
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                statusError = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Inget tal hördes. Tryck på mikrofonen och försök igen."
                    SpeechRecognizer.ERROR_AUDIO ->
                        "Kunde inte läsa från mikrofonen. Kontrollera att den inte används av en annan app."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Mikrofonbehörighet krävs för röstloggning."
                    else ->
                        "Röstigenkänning avbröts (kod $error). Du kan också skriva in din måltid direkt."
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    transcript = if (prefix.isBlank()) text else "$prefix $text"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                    transcript = if (prefix.isBlank()) partial else "$prefix $partial"
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        try {
            recognizer.startListening(buildSpeechIntent())
            isListening = true
        } catch (_: Exception) {
            isListening = false
            try {
                systemSpeechLauncher.launch(buildSpeechIntent())
            } catch (_: Exception) {
                statusError = "Kunde inte starta röstigenkänning. Skriv din måltid i textfältet nedan."
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            startSpeechCapture()
        } else {
            statusError = "Mikrofonbehörighet nekades. Du kan skriva din måltid nedan eller tillåta mikrofon i inställningarna."
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse"
    )

    ModalBottomSheet(
        onDismissRequest = {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Röstloggning med AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Berätta vad du åt så räknar Gemini ut ingredienser, gram och makron.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Stäng")
                }
            }

            // Meal category selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "frukost" to "Frukost",
                    "lunch" to "Lunch",
                    "middag" to "Middag",
                    "mellanmal" to "Mellanmål",
                    "ovrigt" to "Övrigt"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedMealType == key,
                        onClick = { selectedMealType = key },
                        label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            // Central animated Microphone Button Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isListening) {
                        EmeraldPrimary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    if (isListening) EmeraldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(if (isListening) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(if (isListening) Color(0xFFEF4444) else EmeraldPrimary)
                            .clickable(enabled = !isAnalyzing) {
                                if (isListening) {
                                    try {
                                        speechRecognizer?.stopListening()
                                    } catch (_: Exception) {
                                    }
                                    isListening = false
                                } else if (hasAudioPermission) {
                                    startSpeechCapture()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            .testTag("voice_sheet_mic_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stoppa inspelning" else "Starta röstinmatning",
                            tint = if (isListening) Color.White else Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Text(
                        text = if (isListening) {
                            "Lyssnar på svenska... Tryck för att stoppa"
                        } else {
                            "Tryck på mikrofonen och berätta vad du åt"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isListening) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Editable transcript field
            OutlinedTextField(
                value = transcript,
                onValueChange = {
                    transcript = it
                    statusError = null
                },
                label = { Text("Din måltidsbeskrivning") },
                placeholder = { Text("T.ex. Två stekta ägg, en skiva rågbröd med smör och en kopp svart kaffe") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_transcript_input"),
                shape = RoundedCornerShape(14.dp)
            )

            // Quick tappable Swedish examples
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Snabbexempel:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Två stekta ägg, en skiva rågbröd med smör och en kopp svart kaffe",
                        "2 dl havregrynsgröt med 1 dl lättmjölk, 1 banan och en klick jordnötssmör",
                        "150 g grillad kycklingfilé med 2 dl basmatiris, broccoli och 1 msk sweet chili"
                    ).forEach { example ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isAnalyzing) {
                                    transcript = example
                                    statusError = null
                                }
                        ) {
                            Text(
                                text = example,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            if (statusError != null) {
                Text(
                    text = statusError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Analyze CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (transcript.isNotBlank() && !isAnalyzing) {
                    OutlinedButton(
                        onClick = {
                            transcript = ""
                            statusError = null
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text("Rensa")
                    }
                }

                Button(
                    onClick = {
                        if (transcript.isBlank()) {
                            statusError = "Tala in eller skriv vad du åt först."
                            return@Button
                        }
                        if (isListening) {
                            try {
                                speechRecognizer?.stopListening()
                            } catch (_: Exception) {
                            }
                            isListening = false
                        }
                        isAnalyzing = true
                        statusError = null
                        coroutineScope.launch {
                            try {
                                val res = aiNutritionService.parseSpokenMealDescription(
                                    spokenText = transcript,
                                    mealCategoryHint = selectedMealType
                                )
                                isAnalyzing = false
                                res.onSuccess { analysis ->
                                    onMealAnalyzed(analysis, selectedMealType)
                                }.onFailure { err ->
                                    statusError = err.localizedMessage ?: "Kunde inte tolka måltiden."
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                isAnalyzing = false
                                statusError = e.localizedMessage ?: "Ett fel uppstod vid AI-analys."
                            }
                        }
                    },
                    enabled = transcript.isNotBlank() && !isAnalyzing,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("voice_analyze_button")
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini beräknar...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tolka & beräkna med AI", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
