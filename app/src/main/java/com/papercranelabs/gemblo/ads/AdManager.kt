package com.papercranelabs.gemblo.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.papercranelabs.gemblo.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob for Gemblo Blast. Ads start only after Google's consent flow (UMP).
 * Debug builds use Google's TEST ids (safe to tap, never earn).
 * Now: an adaptive banner at the bottom. Rewarded + interstitial come with the store work.
 */
class AdManager(context: Context) {
    private val app = context.applicationContext
    private val started = AtomicBoolean(false)
    var ready by mutableStateOf(false)
        private set

    fun start(activity: Activity) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "consent form: ${error.message}")
                    if (consent.canRequestAds()) initAds()
                }
            },
            { error -> Log.w(TAG, "consent info: ${error.message}") },
        )
        if (consent.canRequestAds()) initAds()
    }

    private fun initAds() {
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(app) { ready = true }
    }

    private companion object { const val TAG = "GembloAds" }
}

/** Bottom banner. Its height is reserved up front so the board never jumps when the ad arrives. */
@Composable
fun BannerAd(ads: AdManager, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val size = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp) }
    Box(
        modifier.fillMaxWidth().height(size.height.dp).background(Color(0x22000000)),
        contentAlignment = Alignment.Center,
    ) {
        if (ads.ready) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(size.height.dp),
                factory = { c ->
                    AdView(c).apply {
                        setAdSize(size)
                        adUnitId = BuildConfig.ADMOB_BANNER
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
