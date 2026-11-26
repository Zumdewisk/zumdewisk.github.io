package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object AdRefreshPolicy {
    const val INTERSTITIAL_COOLDOWN_MS = 15_000L
    const val INTERSTITIAL_MAX_AGE_MS = 50L * 60L * 1000L // 50 min (före AdMobs 60-minutersgräns)
    const val BASE_RETRY_DELAY_MS = 10_000L
    const val MAX_RETRY_DELAY_MS = 60_000L

    fun shouldShowInterstitial(
        tabSwitchCount: Int,
        nowMs: Long,
        lastAdShownAtMs: Long,
        cooldownMs: Long = INTERSTITIAL_COOLDOWN_MS
    ): Boolean {
        if (tabSwitchCount <= 0 || tabSwitchCount % 3 != 0) return false
        return (nowMs - lastAdShownAtMs) >= cooldownMs
    }

    fun isAdFresh(
        loadedAtMs: Long,
        nowMs: Long,
        maxAgeMs: Long = INTERSTITIAL_MAX_AGE_MS
    ): Boolean {
        if (loadedAtMs <= 0L) return false
        return (nowMs - loadedAtMs) < maxAgeMs
    }

    fun computeRetryDelayMs(attempt: Int): Long {
        val clamped = attempt.coerceIn(0, 4)
        return (BASE_RETRY_DELAY_MS shl clamped).coerceAtMost(MAX_RETRY_DELAY_MS)
    }
}

object AdManager {
    private const val TAG = "AdManager"

    // Riktigt AdMob Interstitial Ad Unit ID (Interstitial_Flikbyte)
    private const val INTERSTITIAL_ID = "ca-app-pub-6390888557865245/5142058064"

    private var interstitialAd: InterstitialAd? = null
    private var interstitialLoadedAtMs: Long = 0L

    private val adsInitStarted = AtomicBoolean(false)
    private val consentCheckInFlight = AtomicBoolean(false)
    private val interstitialLoading = AtomicBoolean(false)

    private val _isAdsReady = MutableStateFlow(false)
    val isAdsReady: StateFlow<Boolean> = _isAdsReady.asStateFlow()

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var retryAttempt = 0
    private var pendingRetryRunnable: Runnable? = null

    private var tabSwitchCount = 0
    private var lastAdShownAt = 0L

    /**
     * Initierar UMP GDPR-samtyckesformulär (krävs i EU/EES) samt Google Mobile Ads SDK.
     */
    fun setupConsentAndAds(activity: Activity) {
        val appContext = activity.applicationContext
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        if (consentInfo.canRequestAds()) {
            initAds(appContext)
        }

        if (!consentCheckInFlight.compareAndSet(false, true)) {
            return
        }

        val params = ConsentRequestParameters.Builder().build()
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (activity.isFinishing || activity.isDestroyed) {
                    consentCheckInFlight.set(false)
                    if (consentInfo.canRequestAds()) {
                        initAds(appContext)
                    }
                    return@requestConsentInfoUpdate
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    consentCheckInFlight.set(false)
                    if (formError != null) {
                        Log.w(TAG, "Consent form notice: ${formError.message}")
                    }
                    if (consentInfo.canRequestAds()) {
                        initAds(appContext)
                    }
                }
            },
            { error ->
                consentCheckInFlight.set(false)
                Log.w(TAG, "Consent info update failed: ${error.message}")
                if (consentInfo.canRequestAds()) {
                    initAds(appContext)
                }
            }
        )
    }

    /**
     * Anropas när MainActivity återupptas (t.ex. nästa dag ur bakgrunden)
     * för att säkerställa att samtycke, SDK-initiering och förladdade annonser är färska.
     */
    fun onAppResumed(activity: Activity) {
        if (!_isAdsReady.value) {
            setupConsentAndAds(activity)
            return
        }
        retryAttempt = 0
        ensureInterstitialLoaded(activity.applicationContext)
    }

    private fun initAds(appContext: Context) {
        if (adsInitStarted.getAndSet(true)) {
            if (_isAdsReady.value) {
                ensureInterstitialLoaded(appContext)
            }
            return
        }

        // Konfigurera testenheter för att skydda AdMob-kontot i emulator
        val requestConfiguration = RequestConfiguration.Builder()
            .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
            .build()
        MobileAds.setRequestConfiguration(requestConfiguration)

        MobileAds.initialize(appContext) {
            Log.d(TAG, "MobileAds SDK initialized.")
            _isAdsReady.value = true
            retryAttempt = 0
            loadInterstitial(appContext)
        }
    }

    fun ensureInterstitialLoaded(context: Context) {
        if (!_isAdsReady.value) return
        val now = System.currentTimeMillis()
        val currentAd = interstitialAd
        if (currentAd != null && AdRefreshPolicy.isAdFresh(interstitialLoadedAtMs, now)) {
            return
        }
        if (currentAd != null) {
            Log.d(TAG, "Förladdad interstitial har passerat maxålder; laddar en ny.")
            interstitialAd = null
            interstitialLoadedAtMs = 0L
        }
        loadInterstitial(context.applicationContext)
    }

    fun loadInterstitial(context: Context) {
        if (!_isAdsReady.value) return
        val appContext = context.applicationContext
        if (!interstitialLoading.compareAndSet(false, true)) return

        cancelPendingInterstitialRetry()

        InterstitialAd.load(
            appContext,
            INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading.set(false)
                    interstitialAd = ad
                    interstitialLoadedAtMs = System.currentTimeMillis()
                    retryAttempt = 0
                    Log.d(TAG, "Interstitial-annons laddad.")

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            interstitialLoadedAtMs = 0L
                            lastAdShownAt = System.currentTimeMillis()
                            loadInterstitial(appContext)
                        }

                        override fun onAdFailedToShowFullScreenContent(e: AdError) {
                            Log.e(TAG, "Misslyckades att visa interstitial: ${e.message}")
                            interstitialAd = null
                            interstitialLoadedAtMs = 0L
                            scheduleInterstitialRetry(appContext)
                        }
                    }
                }

                override fun onAdFailedToLoad(e: LoadAdError) {
                    interstitialLoading.set(false)
                    interstitialAd = null
                    interstitialLoadedAtMs = 0L
                    Log.w(TAG, "Laddning av interstitial misslyckades (${e.code}): ${e.message}")
                    scheduleInterstitialRetry(appContext)
                }
            }
        )
    }

    private fun scheduleInterstitialRetry(appContext: Context) {
        cancelPendingInterstitialRetry()
        val delayMs = AdRefreshPolicy.computeRetryDelayMs(retryAttempt)
        retryAttempt = (retryAttempt + 1).coerceAtMost(4)
        Log.d(TAG, "Schemalägger nytt laddningsförsök för interstitial om ${delayMs / 1000}s.")

        val runnable = Runnable {
            pendingRetryRunnable = null
            if (_isAdsReady.value && interstitialAd == null) {
                loadInterstitial(appContext)
            }
        }
        pendingRetryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingInterstitialRetry() {
        pendingRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRetryRunnable = null
    }

    /**
     * Anropas vid flikbyte i appen.
     * Visar en helskärmsannons (interstitial) vid var 3:e flikbyte (med minst 15 sekunders cooldown)
     * och laddar automatiskt om annonsen om den saknas eller har gått ut.
     */
    fun onTabSwitched(activity: Activity) {
        val appContext = activity.applicationContext
        tabSwitchCount++
        val now = System.currentTimeMillis()
        Log.d(TAG, "Flikbyte #$tabSwitchCount registrerat.")

        if (!AdRefreshPolicy.shouldShowInterstitial(tabSwitchCount, now, lastAdShownAt)) {
            ensureInterstitialLoaded(appContext)
            return
        }

        val ad = interstitialAd
        if (ad != null && AdRefreshPolicy.isAdFresh(interstitialLoadedAtMs, now)) {
            Log.d(TAG, "Visar interstitial-annons vid flikbyte #$tabSwitchCount.")
            lastAdShownAt = now
            ad.show(activity)
        } else {
            if (ad != null) {
                Log.d(TAG, "Interstitial var utgången; laddar om inför nästa byte.")
                interstitialAd = null
                interstitialLoadedAtMs = 0L
            } else {
                Log.d(TAG, "Interstitial var inte redo ännu; startar laddning.")
            }
            ensureInterstitialLoaded(appContext)
        }
    }
}
