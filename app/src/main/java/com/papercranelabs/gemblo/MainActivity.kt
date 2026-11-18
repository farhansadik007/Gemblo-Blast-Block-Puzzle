package com.papercranelabs.gemblo

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.papercranelabs.gemblo.ads.AdManager
import com.papercranelabs.gemblo.game.Game

class MainActivity : ComponentActivity() {
    private var sound: SoundFx? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        goFullScreen()
        val prefs = Prefs(this)
        val sfx = SoundFx(this).also { sound = it; it.setMusic(prefs.music) }
        val ads = AdManager(this).also { it.start(this) }
        setContent {
            val ui = remember { GameUi(Game(), prefs, sfx).also { it.hx = Haptics(this) } }
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
