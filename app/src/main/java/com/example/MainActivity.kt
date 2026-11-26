package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.NaringAiTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldPrimary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.example.data.repository.AiNutritionService
import com.example.ui.components.AppUpdateDialog
import com.example.update.AppUpdateManager

sealed interface FirebaseInitState {
    data object Ready : FirebaseInitState
    data object MissingGoogleServicesConfig : FirebaseInitState
    data class Error(val detail: String) : FirebaseInitState
}

private const val TAG = "MainActivity"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val BACKUP_PREFS_NAME = "naringai_appcheck_backup"
private const val BACKUP_DEBUG_TOKEN_KEY = "saved_debug_token"

/**
 * Configures Firebase and installs the appropriate App Check provider factory.
 * In debug builds, DebugAppCheckProviderFactory is installed and the debug token from Intent
 * (or backed-up user debug token) is synchronously committed into SharedPreferences.
 * Returns true if Firebase instance and App Check are verified ready.
 */
fun initializeFirebaseAndAppCheck(context: Context, intent: Intent?): FirebaseInitState {
    return try {
        val hasGoogleAppId = context.resources.getIdentifier("google_app_id", "string", context.packageName) != 0
        if (FirebaseApp.getApps(context).isEmpty()) {
            val initialized = FirebaseApp.initializeApp(context)
            if (initialized == null || FirebaseApp.getApps(context).isEmpty()) {
                Log.e(TAG, "FirebaseApp could not be initialized (hasGoogleAppId=$hasGoogleAppId).")
                return if (!hasGoogleAppId) {
                    FirebaseInitState.MissingGoogleServicesConfig
                } else {
                    FirebaseInitState.Error("FirebaseApp kunde inte initieras.")
                }
            }
            Log.d(TAG, "FirebaseApp initialized.")
        }

        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.setTokenAutoRefreshEnabled(true)

        if (BuildConfig.DEBUG) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            val backupPrefs = context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)

            val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
            if (!intentDebugToken.isNullOrEmpty()) {
                prefs.edit { putString(DEBUG_SECRET_KEY, intentDebugToken) }
                backupPrefs.edit { putString(BACKUP_DEBUG_TOKEN_KEY, intentDebugToken) }
                Log.d(TAG, "App Check debug secret configured from Intent extra.")
            } else {
                val existingStored = prefs.getString(DEBUG_SECRET_KEY, null)?.trim()
                val backedUpToken = backupPrefs.getString(BACKUP_DEBUG_TOKEN_KEY, null)?.trim()
                if (existingStored.isNullOrEmpty() && !backedUpToken.isNullOrEmpty()) {
                    prefs.edit { putString(DEBUG_SECRET_KEY, backedUpToken) }
                    Log.d(TAG, "App Check debug secret restored from backup.")
                } else if (!existingStored.isNullOrEmpty() && backedUpToken.isNullOrEmpty()) {
                    backupPrefs.edit { putString(BACKUP_DEBUG_TOKEN_KEY, existingStored) }
                } else {
                    Log.d(TAG, "Using stored App Check debug secret.")
                }
            }

            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
                true
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed with auto-refresh.")
        } else {
            val resId = context.resources.getIdentifier("recaptcha_site_key", "string", context.packageName)
            val siteKey = if (resId != 0) context.getString(resId) else ""
            if (siteKey.isNotBlank()) {
                appCheck.installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey),
                    true
                )
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed for release builds.")
            } else {
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance(),
                    true
                )
                Log.d(TAG, "PlayIntegrityAppCheckProviderFactory installed for release builds.")
            }
        }
        FirebaseInitState.Ready
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        FirebaseInitState.Error(e.localizedMessage ?: "Okänt fel vid initiering av Firebase.")
    }
}

class MainActivity : ComponentActivity() {
    private var firebaseInitState by mutableStateOf<FirebaseInitState>(FirebaseInitState.Error("Initieras..."))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.example.data.model.SwedishFoodDatabase.initialize(applicationContext)

        // Mandatory App Check configuration with launch intent before any UI or Firebase calls
        firebaseInitState = initializeFirebaseAndAppCheck(this, intent)

        // Initiera UMP GDPR-samtycke och Google Mobile Ads / Interstitial-annonser
        com.example.ads.AdManager.setupConsentAndAds(this)

        enableEdgeToEdge()

        setContent {
            NaringAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(
                        firebaseInitState = firebaseInitState,
                        onRetryFirebase = {
                            firebaseInitState = initializeFirebaseAndAppCheck(this, intent)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.ads.AdManager.onAppResumed(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        firebaseInitState = initializeFirebaseAndAppCheck(this, intent)
    }
}

@Composable
fun AppRoot(
    firebaseInitState: FirebaseInitState,
    onRetryFirebase: () -> Unit
) {
    val isFirebaseReady = firebaseInitState is FirebaseInitState.Ready
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appUpdateManager = remember(context) { AppUpdateManager(context) }

    // Kontrollera automatiskt vid uppstart om det finns en nyare Zevra-APK
    LaunchedEffect(Unit) {
        appUpdateManager.checkForUpdates(manual = false)
    }

    // Automatically warm up / force-refresh App Check token on startup so AI & Firestore work immediately
    // without requiring the user to open Diagnostics and press Spara & Ominstallera + Refresh.
    LaunchedEffect(isFirebaseReady) {
        if (isFirebaseReady) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val appCheck = FirebaseAppCheck.getInstance()
                    appCheck.getAppCheckToken(true).addOnSuccessListener {
                        Log.d(TAG, "Startup App Check token refreshed automatically.")
                    }.addOnFailureListener { err ->
                        Log.w(TAG, "Startup App Check token refresh warning: ${err.message}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Startup App Check warm-up skipped: ${e.message}")
                }
            }
        }
    }

    if (firebaseInitState !is FirebaseInitState.Ready) {
        val isMissingConfig = firebaseInitState is FirebaseInitState.MissingGoogleServicesConfig
        val titleText = if (isMissingConfig) {
            "Firebase-konfiguration saknas i APK:n"
        } else {
            "Kunde inte starta säkerhetstjänster"
        }
        val bodyText = when (firebaseInitState) {
            is FirebaseInitState.MissingGoogleServicesConfig ->
                "Den här APK-filen byggdes utan google-services.json.\n\nLägg till innehållet från app/google-services.json som GitHub Secret (GOOGLE_SERVICES_JSON) och bygg om APK:n, eller generera APK:n direkt från AI Studio."
            is FirebaseInitState.Error ->
                firebaseInitState.detail
            FirebaseInitState.Ready -> ""
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Konfigurationsfel",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Zevra",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onRetryFirebase,
                    modifier = Modifier.testTag("retry_firebase_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Försök igen", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isCheckingAuth by remember { mutableStateOf(currentUser == null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                isCheckingAuth = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    // Auto sign-in attempt on startup if currentUser is not yet initialized
    LaunchedEffect(Unit) {
        val cachedUser = Firebase.auth.currentUser
        if (cachedUser != null) {
            currentUser = cachedUser
            isCheckingAuth = false
            return@LaunchedEffect
        }

        val credentialManager = CredentialManager.create(context)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
                isCheckingAuth = false
            },
            onUnauthenticated = {
                isCheckingAuth = false
            },
            scope = coroutineScope
        )
    }

    if (isCheckingAuth) {
        // High-polish startup splash screen while restoring user session
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.zevra_logo),
                    contentDescription = "Zevra-logotyp",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                )

                Text(
                    text = "Zevra",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = EmeraldPrimary,
                    strokeWidth = 2.5.dp
                )

                Text(
                    text = "Ansluter till ditt konto...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val aiNutritionService = remember { AiNutritionService() }
        AppNavigation(
            currentUserId = currentUser!!.uid,
            aiNutritionService = aiNutritionService,
            appUpdateManager = appUpdateManager
        )
    }

    AppUpdateDialog(appUpdateManager = appUpdateManager)
}
