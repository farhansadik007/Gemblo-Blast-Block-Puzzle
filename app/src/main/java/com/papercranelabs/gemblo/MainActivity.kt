package com.papercranelabs.gemblo

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.papercranelabs.gemblo.ads.AdManager
import com.papercranelabs.gemblo.billing.Store
import com.papercranelabs.gemblo.game.Game

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var sound: SoundFx? = null
    private var gameUi: GameUi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 12+: keep the system launch screen (app icon on white) up a little longer before our studio intro
        if (Build.VERSION.SDK_INT >= 31 && savedInstanceState == null) {
            val shownAt = android.os.SystemClock.uptimeMillis()
            val content = findViewById<android.view.View>(android.R.id.content)
            content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (android.os.SystemClock.uptimeMillis() - shownAt < 1200) { content.postDelayed({ content.invalidate() }, 50); return false }
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            })
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        goFullScreen()
        val prefs = Prefs(this)
        val sfx = SoundFx(this).also { sound = it; it.setMusic(prefs.music) }
        val ads = AdManager(this).also { it.wanted = !prefs.noAds; it.start(this) }
        // a purchase, or one restored after a reinstall: ads off for good
        val store = Store(this) { ads.wanted = false; gameUi?.grantNoAds() ?: run { prefs.noAds = true } }.also { it.start() }
        val host = object : GameUi.Host {
            override val price get() = store.price
            override fun showInterstitial() = ads.showInterstitial(this@MainActivity) {}
            override fun showRewarded(onReward: () -> Unit) = ads.showRewarded(this@MainActivity, onReward)
            override fun buyNoAds() = store.buy(this@MainActivity)
            override fun askNotifications() {
                if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this@MainActivity)) {
                    notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            override fun setReminders(on: Boolean) {
                if (on) Reminders.schedule(this@MainActivity) else Reminders.cancel(this@MainActivity)
            }
        }
        if (prefs.reminders) Reminders.schedule(this)
        // debug: adb shell am start ... --ez test_reminder true  -> posts one reminder right away
        if (BuildConfig.DEBUG && intent.getBooleanExtra("test_reminder", false)) Reminders.post(this)
        setContent {
            val ui = remember {
                GameUi(Game(), prefs, sfx).also {
                    it.hx = Haptics(this); it.host = host; gameUi = it
                    it.demoText = BuildConfig.DEBUG && intent.getBooleanExtra("demo_text", false)
                    if (BuildConfig.DEBUG) intent.getStringExtra("showcase")?.let { k -> it.showcase(k, intent.getIntExtra("theme", -1)) }
                }
            }
            GameScreen(ui, ads)
        }
    }

    /** Immersive full screen: status + navigation bars hidden; a swipe shows them briefly. */
    private fun goFullScreen() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullScreen()   // re-hide after the consent form, an ad, or returning to the app
    }

    override fun onPause() {
        super.onPause()
        sound?.pauseMusic()
        gameUi?.save()   // leaving the app: keep the game so it can be continued
    }

    override fun onResume() {
        super.onResume()
        sound?.resumeMusic()
    }

    override fun onDestroy() {
        sound?.release()
        super.onDestroy()
    }
}
