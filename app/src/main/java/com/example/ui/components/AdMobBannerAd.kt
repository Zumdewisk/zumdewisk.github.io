package com.example.ui.components

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ads.AdManager
import com.example.ads.AdRefreshPolicy
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val BANNER_TAG = "AdMobBannerAd"

/**
 * AdMob Banner-annons som visas i botten av skärmen.
 * Väntar in GDPR-samtycke och MobileAds-initiering, hanterar livscykeln (resume/pause/destroy)
 * och gör automatiska omförsök med backoff om en laddning misslyckas.
 */
@Composable
fun AdMobBannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-6390888557865245/6963948184"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isAdsReady by AdManager.isAdsReady.collectAsState()

    val adView = remember(adUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            setAdUnitId(adUnitId)
        }
    }

    val retryState = remember(adView) {
        object {
            val handler = Handler(Looper.getMainLooper())
            var retryAttempt = 0
            var hasLoadedOnce = false
            var pendingRunnable: Runnable? = null

            fun cancelPendingRetry() {
                pendingRunnable?.let { handler.removeCallbacks(it) }
                pendingRunnable = null
            }

            fun requestBannerLoad() {
                cancelPendingRetry()
                adView.loadAd(AdRequest.Builder().build())
            }
        }
    }

    DisposableEffect(adView, lifecycleOwner) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                retryState.retryAttempt = 0
                retryState.hasLoadedOnce = true
                retryState.cancelPendingRetry()
                Log.d(BANNER_TAG, "Banner-annons laddad.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.w(BANNER_TAG, "Banner-annons misslyckades (${adError.code}): ${adError.message}")
                val delayMs = AdRefreshPolicy.computeRetryDelayMs(retryState.retryAttempt)
                retryState.retryAttempt = (retryState.retryAttempt + 1).coerceAtMost(4)
                retryState.cancelPendingRetry()
                val runnable = Runnable {
                    retryState.pendingRunnable = null
                    if (AdManager.isAdsReady.value) {
                        retryState.requestBannerLoad()
                    }
                }
                retryState.pendingRunnable = runnable
                retryState.handler.postDelayed(runnable, delayMs)
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    adView.resume()
                    if (AdManager.isAdsReady.value && !retryState.hasLoadedOnce) {
                        retryState.retryAttempt = 0
                        retryState.requestBannerLoad()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            retryState.cancelPendingRetry()
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            adView.destroy()
        }
    }

    LaunchedEffect(isAdsReady, adView) {
        if (isAdsReady) {
            retryState.retryAttempt = 0
            retryState.requestBannerLoad()
        }
    }

    if (!isAdsReady) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("admob_banner_ad")
        )
    } else {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("admob_banner_ad"),
            factory = { adView }
        )
    }
}
