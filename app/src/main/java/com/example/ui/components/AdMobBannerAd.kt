package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * AdMob Banner-annons som visas i botten av skärmen.
 * Använder Googles officiella test-AdUnitId för banners som standard.
 */
@Composable
fun AdMobBannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-6390888557865245/6963948184"
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("admob_banner_ad"),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                setAdUnitId(adUnitId)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
