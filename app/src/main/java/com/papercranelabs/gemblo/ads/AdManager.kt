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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.papercranelabs.gemblo.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob for Gemblo Blast. Ads start only after Google's consent flow (UMP).
 * Debug builds use Google's TEST ids (safe to tap, never earn).
 * - adaptive banner at the bottom
 * - interstitial between games (GameUi decides when)
 * - rewarded video for the Revive
 * Nothing loads once the player owns No Ads ([wanted] = false).
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

    /** False once the player owns No Ads: full-screen ads are dropped and never loaded again. */
    var wanted = true
        set(v) { field = v; if (!v) { interstitial = null; rewarded = null } }

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false

    private fun initAds() {
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(app) { ready = true; loadInterstitial(); loadRewarded() }
    }

    private fun loadInterstitial() {
        if (!ready || !wanted || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(app, BuildConfig.ADMOB_INTERSTITIAL, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { loadingInterstitial = false; interstitial = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { loadingInterstitial = false; Log.w(TAG, "interstitial: ${e.message}") }
        })
    }

    private fun loadRewarded() {
        if (!ready || !wanted || rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(app, BuildConfig.ADMOB_REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { loadingRewarded = false; rewarded = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { loadingRewarded = false; Log.w(TAG, "rewarded: ${e.message}") }
        })
    }

    /** Shows the between-games ad if one is loaded; [done] runs when it closes (or right away if there is none). */
    fun showInterstitial(activity: Activity, done: () -> Unit) {
        val ad = interstitial
        if (ad == null || !wanted) { loadInterstitial(); done(); return }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { done(); loadInterstitial() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { done(); loadInterstitial() }
        }
        ad.show(activity)
    }

    /**
     * Rewarded video. [onReward] runs after the video closes, only if it was watched to the end.
     * No video available (offline, no fill, no consent) = the reward is given anyway, so nobody is stuck.
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit) {
        val ad = rewarded
        if (ad == null) { loadRewarded(); onReward(); return }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { if (earned) onReward(); loadRewarded() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { onReward(); loadRewarded() }
        }
        ad.show(activity) { earned = true }
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
