package com.example.ads

import android.app.Activity
import android.content.Context
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
import java.util.concurrent.atomic.AtomicBoolean

object AdManager {
    private const val TAG = "AdManager"

    // Riktigt AdMob Interstitial Ad Unit ID
    private const val INTERSTITIAL_ID = "ca-app-pub-6390888557865245/5142058064"

    private var interstitialAd: InterstitialAd? = null
    private val adsInitialized = AtomicBoolean(false)

    private var tabSwitchCount = 0
    private var lastAdShownAt = 0L

    /**
     * Initierar UMP GDPR-samtyckesformulär (krävs i EU/EES) samt Google Mobile Ads SDK.
     */
    fun setupConsentAndAds(activity: Activity) {
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInfo.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                if (consentInfo.canRequestAds()) {
                    initAds(activity)
                }
            }
        }, { error ->
            Log.w(TAG, "Consent info update failed: ${error.message}")
            if (consentInfo.canRequestAds()) {
                initAds(activity)
            }
        })

        // Om samtycke redan finns från tidigare start
        if (consentInfo.canRequestAds()) {
            initAds(activity)
        }
    }

    private fun initAds(context: Context) {
        if (adsInitialized.getAndSet(true)) return

        // Konfigurera testenheter för att skydda AdMob-kontot under utveckling
        val requestConfiguration = RequestConfiguration.Builder()
            .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
            .build()
        MobileAds.setRequestConfiguration(requestConfiguration)

        MobileAds.initialize(context) {
            Log.d(TAG, "MobileAds SDK initialized.")
            loadInterstitial(context)
        }
    }

    fun loadInterstitial(context: Context) {
        InterstitialAd.load(
            context,
            INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial-annons laddad.")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitial(context) // Förladda nästa
                        }

                        override fun onAdFailedToShowFullScreenContent(e: AdError) {
                            Log.e(TAG, "Misslyckades att visa interstitial: ${e.message}")
                            interstitialAd = null
                            loadInterstitial(context)
                        }
                    }
                }

                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.w(TAG, "Laddning av interstitial misslyckades: ${e.message}")
                    interstitialAd = null
                }
            }
        )
    }

    /**
     * Anropas vid flikbyte i appen.
     * Visar en helskärmsannons (interstitial) vid var 3:e flikbyte (med minst 25 sekunders intervall).
     */
    fun onTabSwitched(activity: Activity) {
        tabSwitchCount++
        val now = System.currentTimeMillis()
        Log.d(TAG, "Flikbyte #$tabSwitchCount registrerat.")
        if (tabSwitchCount % 3 == 0 && now - lastAdShownAt > 25_000) {
            interstitialAd?.let { ad ->
                Log.d(TAG, "Visar interstitial-annons vid flikbyte #$tabSwitchCount.")
                ad.show(activity)
                lastAdShownAt = now
            } ?: Log.d(TAG, "Interstitial var inte redo ännu.")
        }
    }
}
