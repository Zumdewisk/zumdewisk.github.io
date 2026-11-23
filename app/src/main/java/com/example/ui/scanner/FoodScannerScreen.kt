package com.example.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import com.example.data.local.FavoritesManager
import com.example.data.model.SwedishFoodEntry
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.repository.AiMealAnalysisResult
import com.example.data.repository.AiNutritionService
import com.example.data.repository.StorageRepository
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.util.formatMacroDecimal
import com.example.ui.theme.MacroCarbs
import com.example.ui.theme.MacroFat
import com.example.ui.theme.MacroProtein
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "FoodScannerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun FoodScannerScreen(
    aiNutritionService: AiNutritionService,
    storageRepository: StorageRepository? = null,
    onAnalysisCompleted: (AiMealAnalysisResult, String) -> Unit,
    onManualSearchRequested: () -> Unit,
    onDismiss: () -> Unit,
    autoLaunchCamera: Boolean = false,
    autoLaunchGallery: Boolean = false
) {
    val context = LocalContext.current
    val effectiveStorageRepository = storageRepository ?: remember(context) { StorageRepository(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraInitFailed by remember { mutableStateOf(false) }
    var cameraRebindKey by remember { mutableIntStateOf(0) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Shutter flash animation
    val flashAlpha = remember { Animatable(0f) }

    // Scanner Mode: 0 = AI Matkamera (Plate / Image), 1 = Streckkodsläsare (Barcode / EAN), 2 = Näringstabell (OCR/Vision)
    var scanMode by remember { mutableIntStateOf(0) }
    var detectedBarcode by remember { mutableStateOf<String?>(null) }
    var scannedFoodEntry by remember { mutableStateOf<SwedishFoodEntry?>(null) }
    var lastScannedTimestamp by remember { mutableStateOf(0L) }

    // Analysis & Upload State
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("AI förbereder analys...") }
    var analysisJob by remember { mutableStateOf<Job?>(null) }

    // Refresh permission and rebind camera if returning from background or permission dialog
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted != hasCameraPermission) {
                    hasCameraPermission = granted
                }
                if (granted && cameraInitFailed) {
                    cameraInitFailed = false
                    cameraRebindKey++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Barcode Lookup logic
    fun processScannedBarcode(rawBarcode: String) {
        val clean = rawBarcode.trim()
        if (clean.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastScannedTimestamp < 2500L && clean == detectedBarcode) {
            return // Debounce repeat scans of same code
        }
        lastScannedTimestamp = now
        detectedBarcode = clean
        isAnalyzing = true
        statusMessage = "Söker upp streckkod: $clean..."
        errorMessage = null

        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        coroutineScope.launch {
            try {
                val result = aiNutritionService.lookupBarcodeGlobal(clean)
                val entry = result.getOrNull()
                isAnalyzing = false
                if (entry != null) {
                    scannedFoodEntry = entry
                    // Open interactive portion sizing sheet directly on scanner
                } else {
                    errorMessage = "Ingen vara med streckkoden $clean hittades i databasen. Fota baksidans näringsdeklaration för att läsa in produkten direkt!"
                }
            } catch (t: Throwable) {
                isAnalyzing = false
                errorMessage = "Kunde inte söka upp streckkod: ${t.localizedMessage ?: t.message}"
            }
        }
    }

    // OCR / Vision processing for nutrition facts tables on packaging back
    fun startNutritionLabelProcessing(rawBitmap: Bitmap, barcodeHint: String? = detectedBarcode) {
        val bitmap = safelyDownsampleBitmap(rawBitmap, 1024)
        selectedBitmap = bitmap
        isAnalyzing = true
        errorMessage = null
        statusMessage = "Läser av näringstabell per 100 g med Gemini Vision..."

        analysisJob?.cancel()
        analysisJob = coroutineScope.launch {
            try {
                val res = aiNutritionService.analyzeNutritionLabelImage(bitmap, barcodeHint = barcodeHint)
                isAnalyzing = false
                res.onSuccess { entry ->
                    FavoritesManager.saveScannedProduct(context, entry)
                    scannedFoodEntry = entry
                }.onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Kunde inte läsa av näringsdeklarationen. Prova med bättre ljus och skärpa."
                }
            } catch (e: CancellationException) {
                isAnalyzing = false
                throw e
            } catch (t: Throwable) {
                isAnalyzing = false
                errorMessage = "Fel vid skanning av näringstabell: ${t.localizedMessage ?: t.message}"
            }
        }
    }

    // Process image function: calls Gemini AI and optionally uploads in background
    fun startImageProcessing(rawBitmap: Bitmap, sampleMealHint: String? = null) {
        val bitmap = safelyDownsampleBitmap(rawBitmap, 800)
        selectedBitmap = bitmap
        AiNutritionService.lastBitmap = bitmap
        isAnalyzing = true
        errorMessage = null
        statusMessage = "Förbereder bild och startar AI-analys..."

        analysisJob?.cancel()
        analysisJob = coroutineScope.launch {
            val progressJob = launch {
                delay(1500L)
                statusMessage = "Identifierar livsmedel på tallriken..."
                delay(2500L)
                statusMessage = "Uppskattar portionsstorlekar..."
                delay(3000L)
                statusMessage = "Hämtar näringsvärden från svensk databas..."
            }
            try {
                val mealId = "meal_${UUID.randomUUID()}"

                // Kick off background storage upload without blocking AI analysis
                val storageDeferred = async(Dispatchers.IO) {
                    try {
                        effectiveStorageRepository.uploadMealImage(bitmap, mealId = mealId).getOrDefault("")
                    } catch (t: Throwable) {
                        ""
                    }
                }

                // Call Gemini AI Vision model
                val analysisResult = aiNutritionService.analyzeFoodImage(bitmap, mealHint = sampleMealHint)
                progressJob.cancel()

                // Optional download URL if storage already finished
                val downloadUrl = try {
                    withTimeoutOrNull(1000L) { storageDeferred.await() } ?: ""
                } catch (t: Throwable) {
                    ""
                }

                if (analysisResult.isSuccess) {
                    val analysis = analysisResult.getOrThrow()
                    AiNutritionService.lastAnalysisResult = analysis
                    AiNutritionService.lastImageUrl = downloadUrl

                    if (analysis.items.isEmpty()) {
                        isAnalyzing = false
                        errorMessage = "Ingen mat identifierades: ${analysis.reasoning}"
                    } else {
                        isAnalyzing = false
                        onAnalysisCompleted(analysis, downloadUrl)
                    }
                } else {
                    val error = analysisResult.exceptionOrNull()
                    Log.e(TAG, "Gemini AI-analys misslyckades", error)
                    isAnalyzing = false
                    errorMessage = "AI-analysen misslyckades: ${error?.localizedMessage ?: error?.message ?: "Kunde inte analysera bilden"}"
                }
            } catch (e: TimeoutCancellationException) {
                progressJob.cancel()
                isAnalyzing = false
                Log.e(TAG, "Bildanalys timeout i FoodScannerScreen", e)
                errorMessage = "AI-analysen tog för lång tid (timeout). Kontrollera internetanslutningen och försök igen."
            } catch (e: CancellationException) {
                progressJob.cancel()
                isAnalyzing = false
                throw e
            } catch (t: Throwable) {
                progressJob.cancel()
                Log.e(TAG, "Image processing exception", t)
                isAnalyzing = false
                errorMessage = "Ett fel uppstod vid bildanalysen: ${t.localizedMessage ?: t.message}"
            }
        }
    }

    // System camera launcher (direct fallback)
    val systemCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            if (scanMode == 2) {
                startNutritionLabelProcessing(safelyDownsampleBitmap(bitmap, 1024))
            } else {
                startImageProcessing(safelyDownsampleBitmap(bitmap, 800))
            }
        }
    }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val isLabelMode = scanMode == 2
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    val boundsStream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Kunde inte öppna vald bild.")
                    boundsStream.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        throw IllegalStateException("Den valda filen är inte en giltig bild.")
                    }

                    val maxDimension = if (isLabelMode) 1024 else 800
                    var sampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) {
                        sampleSize *= 2
                    }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val imageStream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Kunde inte öppna vald bild.")
                    val bitmap = imageStream.use {
                        BitmapFactory.decodeStream(it, null, options)
                    } ?: throw IllegalStateException("Kunde inte avkoda vald bild.")
                    val safeBmp = safelyDownsampleBitmap(bitmap, maxDimension)
                    withContext(Dispatchers.Main) {
                        if (isLabelMode) {
                            startNutritionLabelProcessing(safeBmp)
                        } else {
                            startImageProcessing(safeBmp)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to load image from gallery", t)
                    withContext(Dispatchers.Main) {
                        errorMessage = "Fel vid läsning av bild: ${t.localizedMessage}"
                    }
                }
            }
        }
    }

    // Camera Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            cameraInitFailed = false
            cameraRebindKey++
            errorMessage = null
        } else {
            errorMessage = "Kamerabehörighet behövs för att fotografera mat."
        }
    }

    // Auto-launch actions on first composition if requested
    LaunchedEffect(Unit) {
        if (!hasCameraPermission && autoLaunchCamera) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else if (autoLaunchGallery) {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    // Robust CameraX binding attached directly to previewView's surfaceProvider.
    // Binds 2 use cases per mode (Preview + ImageCapture in photo mode, Preview + ImageAnalysis fallback in barcode mode)
    // so LEGACY/LIMITED devices and emulators never fail 3-stream surface combination limits.
    LaunchedEffect(hasCameraPermission, lensFacing, scanMode, cameraRebindKey) {
        if (!hasCameraPermission) return@LaunchedEffect

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Resolve available camera selector without crashing if lens is unavailable
                val targetSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val selector = when {
                    provider.hasCamera(targetSelector) -> targetSelector
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                val camera = if (scanMode == 1) {
                    val barcodeScanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(
                                Barcode.FORMAT_EAN_13,
                                Barcode.FORMAT_EAN_8,
                                Barcode.FORMAT_UPC_A,
                                Barcode.FORMAT_UPC_E,
                                Barcode.FORMAT_QR_CODE,
                                Barcode.FORMAT_CODE_128,
                                Barcode.FORMAT_CODE_39
                            )
                            .build()
                    )

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && scanMode == 1 && !isAnalyzing && scannedFoodEntry == null) {
                            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    val first = barcodes.firstOrNull()?.rawValue
                                    if (!first.isNullOrBlank()) {
                                        mainExecutor.execute {
                                            processScannedBarcode(first)
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        imageCapture = capture
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, imageAnalysis)
                    } catch (threeUseCaseEx: Exception) {
                        Log.w(TAG, "3 use cases unsupported; falling back to Preview + ImageAnalysis for barcode mode", threeUseCaseEx)
                        provider.unbindAll()
                        imageCapture = null
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
                    }
                } else {
                    imageCapture = capture
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                }

                cameraControl = camera.cameraControl
                cameraControl?.enableTorch(isFlashOn)
                cameraInitFailed = false
            } catch (e: Exception) {
                Log.e(TAG, "CameraX initialization failed", e)
                cameraInitFailed = true
            }
        }, mainExecutor)
    }

    // Unbind camera and shut down background executor on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding camera on dispose", e)
            }
            try {
                analysisExecutor.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    // Toggle flash / torch
    fun toggleFlash() {
        val next = !isFlashOn
        isFlashOn = next
        cameraControl?.enableTorch(next)
    }

    // Flip lens
    fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        isFlashOn = false
    }

    // Capture photo from CameraX
    fun takePhoto() {
        val capture = imageCapture
        if (capture == null) {
            val previewBmp = previewView.bitmap
            if (previewBmp != null) {
                val targetMax = if (scanMode == 2) 1024 else 800
                val finalBitmap = safelyDownsampleBitmap(previewBmp, targetMax)
                when (scanMode) {
                    1 -> {
                        isAnalyzing = true
                        statusMessage = "Läser av streckkod..."
                        val inputImage = InputImage.fromBitmap(finalBitmap, 0)
                        BarcodeScanning.getClient().process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                val first = barcodes.firstOrNull()?.rawValue
                                if (!first.isNullOrBlank()) {
                                    processScannedBarcode(first)
                                } else {
                                    startImageProcessing(finalBitmap)
                                }
                            }
                            .addOnFailureListener {
                                startImageProcessing(finalBitmap)
                            }
                    }
                    2 -> startNutritionLabelProcessing(finalBitmap)
                    else -> startImageProcessing(finalBitmap)
                }
            } else {
                systemCameraLauncher.launch(null)
            }
            return
        }

        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        // Shutter flash effect
        coroutineScope.launch {
            flashAlpha.snapTo(0.85f)
            flashAlpha.animateTo(0f, tween(250))
        }

        isAnalyzing = true
        statusMessage = "Tar bild..."
        errorMessage = null

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    coroutineScope.launch(Dispatchers.Default) {
                        try {
                            val rawBitmap = imageProxyToBitmap(imageProxy)
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val rotatedBitmap = if (rawBitmap != null && rotation != 0) {
                                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                            } else {
                                rawBitmap
                            }

                            val maxDim = if (scanMode == 2) 1024 else 800
                            val finalBitmap = rotatedBitmap?.let { safelyDownsampleBitmap(it, maxDim) }

                            if (scanMode == 1 && finalBitmap != null) {
                                // Try barcode scan on captured high-res frame
                                val inputImage = InputImage.fromBitmap(finalBitmap, 0)
                                val scanner = BarcodeScanning.getClient()
                                scanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        val first = barcodes.firstOrNull()?.rawValue
                                        if (!first.isNullOrBlank()) {
                                            processScannedBarcode(first)
                                        } else {
                                            // Fallback to nutrition label OCR or food vision if no barcode detected
                                            startImageProcessing(finalBitmap)
                                        }
                                    }
                                    .addOnFailureListener {
                                        startImageProcessing(finalBitmap)
                                    }
                            } else {
                                withContext(Dispatchers.Main) {
                                    if (finalBitmap != null) {
                                        if (scanMode == 2) {
                                            startNutritionLabelProcessing(finalBitmap)
                                        } else {
                                            startImageProcessing(finalBitmap)
                                        }
                                    } else {
                                        isAnalyzing = false
                                        errorMessage = "Kunde inte läsa bilden från kameran. Prova igen eller använd systemkameran."
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error handling captured image", t)
                            withContext(Dispatchers.Main) {
                                isAnalyzing = false
                                errorMessage = "Fel vid bildbehandling: ${t.localizedMessage}"
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    isAnalyzing = false
                    errorMessage = "Kunde inte ta bilden (${exception.localizedMessage}). Tryck nedan för att använda systemkameran."
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Viewfinder Area
            if (hasCameraPermission && !cameraInitFailed) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Food Plate / Barcode Framing Reticle
                PlateFramingOverlay(
                    scanMode = scanMode,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Permission Denied or Camera Unavailable Fallback Screen
                CameraFallbackScreen(
                    hasPermission = hasCameraPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onRetryCamera = {
                        cameraInitFailed = false
                        cameraRebindKey++
                    },
                    onOpenSystemCamera = { systemCameraLauncher.launch(null) },
                    onOpenGallery = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onTestSampleMeal = { sampleRes ->
                        val bmp = BitmapFactory.decodeResource(context.resources, sampleRes)
                        val hint = when (sampleRes) {
                            R.drawable.img_salmon_plate -> "Grillad lax med ugnsrostad potatis och sparris"
                            R.drawable.img_chicken_salad -> "Kycklingsallad med avokado och quinoa"
                            R.drawable.img_oatmeal_bowl -> "Havregrynsgröt med färska bär och chiafrön"
                            else -> null
                        }
                        startImageProcessing(bmp, hint)
                    },
                    onManualSearch = onManualSearchRequested
                )
            }

            // Top Bar with dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .testTag("camera_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Stäng", tint = Color.White)
                    }

                    // Mode Switcher: Måltid vs Streckkod vs Näringstabell
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // AI Camera Mode Tab
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (scanMode == 0) EmeraldPrimary else Color.Transparent,
                                modifier = Modifier
                                    .clickable {
                                        scanMode = 0
                                        detectedBarcode = null
                                    }
                                    .testTag("scanner_mode_ai_photo")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (scanMode == 0) Color.Black else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Måltid",
                                        color = if (scanMode == 0) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Barcode Scanner Mode Tab
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (scanMode == 1) EmeraldPrimary else Color.Transparent,
                                modifier = Modifier
                                    .clickable { scanMode = 1 }
                                    .testTag("scanner_mode_barcode")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = if (scanMode == 1) Color.Black else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Streckkod",
                                        color = if (scanMode == 1) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Nutrition Label OCR/Vision Mode Tab
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (scanMode == 2) EmeraldPrimary else Color.Transparent,
                                modifier = Modifier
                                    .clickable { scanMode = 2 }
                                    .testTag("scanner_mode_nutrition_label")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = if (scanMode == 2) Color.Black else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Tabell",
                                        color = if (scanMode == 2) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Controls: Flash & Flip
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasCameraPermission && !cameraInitFailed) {
                            IconButton(
                                onClick = { toggleFlash() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .testTag("camera_flash_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = if (isFlashOn) "Blixt på" else "Blixt av",
                                    tint = if (isFlashOn) Color.Yellow else Color.White
                                )
                            }

                            IconButton(
                                onClick = { flipCamera() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .testTag("camera_flip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlipCameraAndroid,
                                    contentDescription = "Vänd kamera",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Control Bar with Dark Gradient Scrim (Solves low contrast on bright camera feeds)
            if (hasCameraPermission && !cameraInitFailed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Black.copy(alpha = 0.96f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(top = 20.dp, bottom = 18.dp, start = 16.dp, end = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Quick Sample Test Bar (Adapted to scanMode)
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when (scanMode) {
                                        1 -> "Testa streckkod:"
                                        2 -> "Näringstabell (OCR):"
                                        else -> "Testa mat:"
                                    },
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )

                                when (scanMode) {
                                    1 -> {
                                        SampleMealPill(
                                            emoji = "🥜",
                                            label = "Garant Jordnöt",
                                            onClick = { processScannedBarcode("7330797084532") }
                                        )
                                        SampleMealPill(
                                            emoji = "🥛",
                                            label = "Arla Mjölk",
                                            onClick = { processScannedBarcode("7310865000378") }
                                        )
                                        SampleMealPill(
                                            emoji = "🌾",
                                            label = "AXA Havregryn",
                                            onClick = { processScannedBarcode("7310751160032") }
                                        )
                                        SampleMealPill(
                                            emoji = "🥣",
                                            label = "Keso",
                                            onClick = { processScannedBarcode("7310865080011") }
                                        )
                                    }
                                    2 -> {
                                        Text(
                                            text = "Fota tabellen per 100 g/ml så skapar Gemini produkten direkt",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    else -> {
                                        SampleMealPill(
                                            emoji = "🥗",
                                            label = "Sallad",
                                            onClick = {
                                                val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_chicken_salad)
                                                startImageProcessing(bmp, null)
                                            }
                                        )

                                        SampleMealPill(
                                            emoji = "🐟",
                                            label = "Lax & potatis",
                                            onClick = {
                                                val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_salmon_plate)
                                                startImageProcessing(bmp, null)
                                            }
                                        )

                                        SampleMealPill(
                                            emoji = "🥣",
                                            label = "Gröt & bär",
                                            onClick = {
                                                val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_oatmeal_bowl)
                                                startImageProcessing(bmp, null)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Gallery Button
                            CameraActionButton(
                                icon = Icons.Default.PhotoLibrary,
                                label = "Galleri",
                                tag = "camera_gallery_picker",
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )

                            // System Camera App Button
                            CameraActionButton(
                                icon = Icons.Default.PhotoCamera,
                                label = "Systemkamera",
                                tag = "camera_system_launcher",
                                onClick = { systemCameraLauncher.launch(null) }
                            )

                            // Big High-Contrast Action Button
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(4.dp, Color.White, CircleShape)
                                    .padding(5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        takePhoto()
                                    }
                                    .testTag("camera_shutter_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (scanMode) {
                                        1 -> Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = "Skanna streckkod",
                                            tint = Color.Black,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        2 -> Icon(
                                            imageVector = Icons.Default.ReceiptLong,
                                            contentDescription = "Fota näringstabell",
                                            tint = Color.Black,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }

                            // Manual Search Button
                            CameraActionButton(
                                icon = Icons.Default.Search,
                                label = "Sök",
                                tag = "camera_manual_search",
                                onClick = onManualSearchRequested
                            )
                        }
                    }
                }
            }

            // Visual Shutter Flash
            if (flashAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = flashAlpha.value))
                )
            }

            // Processing & Analysis Overlay
            AnimatedVisibility(
                visible = isAnalyzing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AnalysisOverlay(
                    statusMessage = statusMessage,
                    previewBitmap = selectedBitmap,
                    onCancel = {
                        analysisJob?.cancel()
                        isAnalyzing = false
                    }
                )
            }

            // Error Toast/Card with direct 1-click food logging alternatives if network dropped
            if (errorMessage != null && !isAnalyzing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        if (scanMode == 1 || detectedBarcode != null) {
                            Button(
                                onClick = {
                                    scanMode = 2
                                    errorMessage = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("scan_nutrition_label_fallback_button")
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fota baksidans näringsdeklaration", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (scanMode != 2) {
                            Text(
                                text = "Välj vad du fotograferade för att logga direkt med exakta näringsvärden:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onAnalysisCompleted(aiNutritionService.getSmartFallbackAnalysis("äpple"), "")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🍎 Äpple", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        onAnalysisCompleted(aiNutritionService.getSmartFallbackAnalysis("banan"), "")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🍌 Banan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        onAnalysisCompleted(aiNutritionService.getSmartFallbackAnalysis("smörgås"), "")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🥪 Macka", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onAnalysisCompleted(aiNutritionService.getSmartFallbackAnalysis("sallad"), "")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🥗 Sallad", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        onAnalysisCompleted(aiNutritionService.getSmartFallbackAnalysis("lax"), "")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🍲 Varmrätt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        errorMessage = null
                                        onManualSearchRequested()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🔍 Sök", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🖼️ Välj bild", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        onManualSearchRequested()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🔍 Sök manuellt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { errorMessage = null },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ta nytt foto", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Live Barcode or Nutrition Label Portion & Serving Size Editor Sheet
        scannedFoodEntry?.let { entry ->
            val isNutritionLabel = entry.id.startsWith("label_") || entry.category.contains("Näringsdeklaration", ignoreCase = true)
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var amountGramsText by remember(entry) {
                mutableStateOf(entry.standardPortionGrams.coerceAtLeast(10).toString())
            }
            val currentGrams = amountGramsText.toIntOrNull()?.coerceAtLeast(1) ?: entry.standardPortionGrams.coerceAtLeast(10)
            val calculatedIngredient = remember(entry, currentGrams) {
                entry.toIngredient(currentGrams)
            }

            ModalBottomSheet(
                onDismissRequest = { scannedFoodEntry = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header with badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isNutritionLabel) Icons.Default.ReceiptLong else Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isNutritionLabel) "Näringstabell avläst (OCR)" else "Streckkod avläst",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Text(
                                text = entry.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Per 100g: ${entry.caloriesPer100g} kcal • P ${formatMacroDecimal(entry.proteinPer100g)}g • K ${formatMacroDecimal(entry.carbsPer100g)}g • F ${formatMacroDecimal(entry.fatPer100g)}g",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (entry.category.isNotBlank()) {
                                Text(
                                    text = entry.category,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { scannedFoodEntry = null },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Stäng",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Interactive Grams/Portion Size Input
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Välj portionsstorlek:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = amountGramsText,
                                onValueChange = { amountGramsText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Mängd i gram") },
                                trailingIcon = { Text("g", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("barcode_grams_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick preset chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val presets = listOf(
                                    "1 portion (${entry.standardPortionGrams}g)" to entry.standardPortionGrams,
                                    "50 g" to 50,
                                    "100 g" to 100,
                                    "150 g" to 150,
                                    "200 g" to 200
                                ).distinctBy { it.second }
                                presets.forEach { (label, grams) ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (currentGrams == grams) EmeraldPrimary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (currentGrams == grams) EmeraldDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { amountGramsText = grams.toString() }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (currentGrams == grams) Color.Black else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Dynamic Real-time Nutrition Calculated for this portion
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = EmeraldPrimary.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Näring för ${currentGrams}g:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${calculatedIngredient.calories} kcal",
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
                                Text("P: ${formatMacroDecimal(calculatedIngredient.protein)}g", fontSize = 12.sp, color = MacroProtein, fontWeight = FontWeight.Bold)
                                Text("K: ${formatMacroDecimal(calculatedIngredient.carbs)}g", fontSize = 12.sp, color = MacroCarbs, fontWeight = FontWeight.Bold)
                                Text("F: ${formatMacroDecimal(calculatedIngredient.fat)}g", fontSize = 12.sp, color = MacroFat, fontWeight = FontWeight.Bold)
                                Text("Fiber: ${formatMacroDecimal(calculatedIngredient.fiber)}g", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Confirm and continue button
                    Button(
                        onClick = {
                            val finalIngredient = entry.toIngredient(currentGrams)
                            val sourceReasoning = when {
                                isNutritionLabel && !detectedBarcode.isNullOrBlank() ->
                                    "Avläst från förpackningens näringsdeklaration (kopplad till streckkod $detectedBarcode) med ${currentGrams}g portion."
                                isNutritionLabel ->
                                    "Avläst från förpackningens näringsdeklaration med ${currentGrams}g portion."
                                !detectedBarcode.isNullOrBlank() ->
                                    "Identifierad via streckkod ($detectedBarcode). Hämtad från livsmedelsdatabas med ${currentGrams}g portion."
                                else ->
                                    "Hämtad från produktdatabas med ${currentGrams}g portion."
                            }
                            val analysis = AiMealAnalysisResult(
                                mealName = entry.displayName,
                                confidence = "high",
                                reasoning = sourceReasoning,
                                totalCalories = finalIngredient.calories,
                                totalProteinG = finalIngredient.protein,
                                totalCarbsG = finalIngredient.carbs,
                                totalFatG = finalIngredient.fat,
                                totalFiberG = finalIngredient.fiber,
                                totalSugarG = finalIngredient.sugar,
                                totalSaltG = finalIngredient.salt,
                                items = listOf(finalIngredient)
                            )
                            scannedFoodEntry = null
                            AiNutritionService.lastAnalysisResult = analysis
                            AiNutritionService.lastImageUrl = ""
                            onAnalysisCompleted(analysis, "")
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_scanned_barcode_button")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fortsätt med ${currentGrams}g (${calculatedIngredient.calories} kcal)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}


/**
 * Clickable sample meal pill for quick testing in emulator
 */
@Composable
private fun SampleMealPill(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * High contrast camera control action button
 */
@Composable
private fun CameraActionButton(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Safely downsamples any Bitmap to max dimension to prevent OutOfMemoryError.
 */
private fun safelyDownsampleBitmap(bitmap: Bitmap, maxDim: Int = 1024): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= maxDim && h <= maxDim) return bitmap
    val scale = maxDim.toFloat() / maxOf(w, h)
    val matrix = Matrix().apply { postScale(scale, scale) }
    return try {
        Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)
    } catch (t: Throwable) {
        bitmap
    }
}

/**
 * Robust imageProxy to Bitmap converter with byte buffer fallback.
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        imageProxy.toBitmap()
    } catch (t: Throwable) {
        try {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (t2: Throwable) {
            Log.e(TAG, "Fallback decoding failed", t2)
            null
        }
    }
}

/**
 * Semi-transparent overlay with a plate alignment guide or barcode scan guide reticle.
 */
@Composable
private fun PlateFramingOverlay(
    scanMode: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_progress"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (scanMode == 1) {
                // Barcode Mode: Horizontal rectangle framing (aspect ratio 2.3:1)
                val rectWidth = canvasWidth * 0.85f
                val rectHeight = rectWidth * 0.45f
                val left = (canvasWidth - rectWidth) / 2f
                val top = (canvasHeight - rectHeight) / 2f - 30f

                val cornerLength = 36f
                val strokeWidth = 6f
                val strokeColor = EmeraldPrimary.copy(alpha = pulseAlpha)

                // Top-Left
                drawLine(strokeColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
                drawLine(strokeColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)

                // Top-Right
                drawLine(strokeColor, Offset(left + rectWidth, top), Offset(left + rectWidth - cornerLength, top), strokeWidth)
                drawLine(strokeColor, Offset(left + rectWidth, top), Offset(left + rectWidth, top + cornerLength), strokeWidth)

                // Bottom-Left
                drawLine(strokeColor, Offset(left, top + rectHeight), Offset(left + cornerLength, top + rectHeight), strokeWidth)
                drawLine(strokeColor, Offset(left, top + rectHeight), Offset(left, top + rectHeight - cornerLength), strokeWidth)

                // Bottom-Right
                drawLine(strokeColor, Offset(left + rectWidth, top + rectHeight), Offset(left + rectWidth - cornerLength, top + rectHeight), strokeWidth)
                drawLine(strokeColor, Offset(left + rectWidth, top + rectHeight), Offset(left + rectWidth, top + rectHeight - cornerLength), strokeWidth)

                // Red/Cyan Laser Scan Line
                val laserY = top + (rectHeight * laserProgress)
                drawLine(
                    color = Color.Red.copy(alpha = 0.85f),
                    start = Offset(left + 10f, laserY),
                    end = Offset(left + rectWidth - 10f, laserY),
                    strokeWidth = 3f
                )
            } else if (scanMode == 2) {
                // Nutrition Table OCR Mode: Tall vertical card reticle
                val rectWidth = canvasWidth * 0.78f
                val rectHeight = minOf(rectWidth * 1.18f, canvasHeight * 0.50f)
                val left = (canvasWidth - rectWidth) / 2f
                val top = (canvasHeight - rectHeight) / 2f - 36f

                val cornerLength = 34f
                val strokeWidth = 5.5f
                val strokeColor = EmeraldPrimary.copy(alpha = pulseAlpha)

                // Top-Left
                drawLine(strokeColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
                drawLine(strokeColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
                // Top-Right
                drawLine(strokeColor, Offset(left + rectWidth, top), Offset(left + rectWidth - cornerLength, top), strokeWidth)
                drawLine(strokeColor, Offset(left + rectWidth, top), Offset(left + rectWidth, top + cornerLength), strokeWidth)
                // Bottom-Left
                drawLine(strokeColor, Offset(left, top + rectHeight), Offset(left + cornerLength, top + rectHeight), strokeWidth)
                drawLine(strokeColor, Offset(left, top + rectHeight), Offset(left, top + rectHeight - cornerLength), strokeWidth)
                // Bottom-Right
                drawLine(strokeColor, Offset(left + rectWidth, top + rectHeight), Offset(left + rectWidth - cornerLength, top + rectHeight), strokeWidth)
                drawLine(strokeColor, Offset(left + rectWidth, top + rectHeight), Offset(left + rectWidth, top + rectHeight - cornerLength), strokeWidth)

                // Subtle dashed inner table guidelines
                listOf(0.25f, 0.5f, 0.75f).forEach { yRatio ->
                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(left + 14f, top + rectHeight * yRatio),
                        end = Offset(left + rectWidth - 14f, top + rectHeight * yRatio),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    )
                }
            } else {
                // Food Plate Mode: Square framing box with inner circle
                val boxSize = minOf(canvasWidth * 0.82f, canvasHeight * 0.50f)
                val left = (canvasWidth - boxSize) / 2f
                val top = (canvasHeight - boxSize) / 2f - 40f

                val cornerLength = 32f
                val strokeWidth = 5f
                val strokeColor = EmeraldPrimary.copy(alpha = pulseAlpha)

                // Top-Left
                drawLine(strokeColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
                drawLine(strokeColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)

                // Top-Right
                drawLine(strokeColor, Offset(left + boxSize, top), Offset(left + boxSize - cornerLength, top), strokeWidth)
                drawLine(strokeColor, Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLength), strokeWidth)

                // Bottom-Left
                drawLine(strokeColor, Offset(left, top + boxSize), Offset(left + cornerLength, top + boxSize), strokeWidth)
                drawLine(strokeColor, Offset(left, top + boxSize), Offset(left, top + boxSize - cornerLength), strokeWidth)

                // Bottom-Right
                drawLine(strokeColor, Offset(left + boxSize, top + boxSize), Offset(left + boxSize - cornerLength, top + boxSize), strokeWidth)
                drawLine(strokeColor, Offset(left + boxSize, top + boxSize), Offset(left + boxSize, top + boxSize - cornerLength), strokeWidth)

                // Subtle dashed inner plate circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = boxSize * 0.44f,
                    center = Offset(left + boxSize / 2f, top + boxSize / 2f),
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                    )
                )
            }
        }

        // Guiding instruction pill positioned neatly under the frame
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = when (scanMode) {
                    1 -> 150.dp
                    2 -> 240.dp
                    else -> 210.dp
                }),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Text(
                text = when (scanMode) {
                    1 -> "Håll streckkoden i rutan så läses den av direkt"
                    2 -> "Rikta kameran mot baksidans näringstabell (per 100g)"
                    else -> "Rikta kameran mot tallriken"
                },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Fallback screen shown when camera permission is denied or camera hardware is unavailable.
 */
@Composable
private fun CameraFallbackScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRetryCamera: () -> Unit,
    onOpenSystemCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onTestSampleMeal: (Int) -> Unit,
    onManualSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(EmeraldPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (!hasPermission) "Kamerabehörighet krävs" else "Kameran är inte tillgänglig",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (!hasPermission) {
                "För att Zevra ska kunna fotografera din mat och identifiera ingredienser och kalorier direkt på skärmen behövs tillgång till kameran."
            } else {
                "Kameran kunde inte startas automatiskt. Tryck på \"Starta kameran igen\" eller välj en bild från galleriet eller en exempelmåltid."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Quick test buttons
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Testa AI-matskanning direkt:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = { onTestSampleMeal(R.drawable.img_salmon_plate) }) {
                        Text("🐟 Lax")
                    }
                    OutlinedButton(onClick = { onTestSampleMeal(R.drawable.img_chicken_salad) }) {
                        Text("🥗 Sallad")
                    }
                    OutlinedButton(onClick = { onTestSampleMeal(R.drawable.img_oatmeal_bowl) }) {
                        Text("🥣 Gröt")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasPermission) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("grant_camera_permission_button"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text("Tillåt kamera", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Button(
                onClick = onRetryCamera,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("retry_camera_button"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Starta kameran igen", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = onOpenGallery,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("fallback_open_gallery_button"),
            shape = RoundedCornerShape(25.dp)
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Välj bild från galleriet", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onManualSearch,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("fallback_manual_search_button"),
            shape = RoundedCornerShape(25.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sök livsmedel manuellt", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Modern High-tech Analysis Overlay
 */
@Composable
private fun AnalysisOverlay(
    statusMessage: String,
    previewBitmap: Bitmap?,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, EmeraldPrimary, RoundedCornerShape(20.dp))
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Analyserad bild",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            CircularProgressIndicator(
                color = EmeraldPrimary,
                strokeWidth = 3.5.dp,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Analyserar måltid...",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusMessage,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Avbryt analys", fontSize = 13.sp)
            }
        }
    }
}
