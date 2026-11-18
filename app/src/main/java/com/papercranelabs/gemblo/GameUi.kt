package com.papercranelabs.gemblo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.papercranelabs.gemblo.game.Cell
import com.papercranelabs.gemblo.game.Game
import com.papercranelabs.gemblo.game.N
import com.papercranelabs.gemblo.game.Piece
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gemblo", Context.MODE_PRIVATE)
    var best: Int
        get() = sp.getInt("best", 0)
        set(v) { sp.edit().putInt("best", v).apply() }
    var muted: Boolean
        get() = sp.getBoolean("muted", false)
        set(v) { sp.edit().putBoolean("muted", v).apply() }
    var music: Boolean
        get() = sp.getBoolean("music", true)
        set(v) { sp.edit().putBoolean("music", v).apply() }
    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) { sp.edit().putBoolean("vibrate", v).apply() }
    /** Test build: set by the "No Ads" button. Real build: set by the Play Billing purchase. */
    var noAds: Boolean
        get() = sp.getBoolean("no_ads", false)
        set(v) { sp.edit().putBoolean("no_ads", v).apply() }
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }
}

enum class Screen { Home, Game }

class FxClear(val r: Int, val c: Int, val color: Int, val t0: Long)
class FxBeam(val isRow: Boolean, val index: Int, val color: Int, val t0: Long)
class FxText(val text: String, val x: Float, val y: Float, val color: Color, val size: Float, val t0: Long, val rainbow: Boolean = false)
/**
 * Points that pop at the grid's center (x, y = canvas px) and fly into the score counter.
 * hold = pop and linger first (line clears); otherwise fly straight away (plain placements).
 */
class ScoreFly(val value: Int, val x: Float, val y: Float, val t0: Long, val hold: Boolean) {
    val flyAt = if (hold) 520 else 120
    val total = flyAt + if (hold) 420 else 380
}
class FxRing(val x: Float, val y: Float, val color: Int, val t0: Long, val big: Boolean)
/** sx/sy = where the finger went down; the piece moves faster than the finger (see [GameUi.eff]). */
class Drag(val slot: Int, val sx: Float, val sy: Float, var x: Float, var y: Float, val t0: Long)
class Return(val slot: Int, val from: Offset, val t0: Long)

/** Pixel layout of the board + tray canvas. */
class Metrics(val w: Float, val h: Float, val dp: Float) {
    /** Whole-pixel line width: the same between every cell and around the outside. */
    val gap = maxOf(2f, kotlin.math.round(1.5f * dp))
    /** Whole-pixel cell size, so every gap is identical. */
    val cell = floor((w - 9 * gap) / N)
    val step = cell + gap
    /** Left/top of the first cell (grid centered, outer border = one gap). */
    val pad = floor((w - (N * cell + (N - 1) * gap)) / 2)
    val gridLeft = pad - gap
    val gridSize = N * cell + (N + 1) * gap
    val trayTop = w + 16 * dp
    val trayH = h - trayTop
    val trayCell = floor(cell * 0.64f)
    val trayStep = trayCell + gap * 0.6f

    fun cellX(c: Int) = pad + c * step
    fun cellY(r: Int) = pad + r * step

    fun hitSlot(x: Float, y: Float): Int? =
        if (y >= trayTop - 8 * dp) (x / (w / 3f)).toInt().coerceIn(0, 2) else null

    fun slotCenter(i: Int) = Offset(w * (i + 0.5f) / 3f, trayTop + trayH / 2f)

    fun trayTopLeft(p: Piece, i: Int): Offset {
        val ctr = slotCenter(i)
        return Offset(ctr.x - (p.w * trayStep - gap * 0.6f) / 2, ctr.y - (p.h * trayStep - gap * 0.6f) / 2)
    }

    /** The dragged piece floats above the finger so the finger never hides it. */
    fun dragTopLeft(p: Piece, x: Float, y: Float) =
        Offset(x - (p.w * step - gap) / 2f, y - p.h * step - cell * 0.8f)

    /** Fractional board position of the piece's top-left cell. */
    fun snapF(p: Piece, x: Float, y: Float): Offset {
        val tl = dragTopLeft(p, x, y)
        return Offset((tl.x - pad) / step, (tl.y - pad) / step)
    }
}

class GameUi(var game: Game, private val prefs: Prefs, private val sound: SoundFx?) {
    var screen by mutableStateOf(Screen.Home)
    var now by mutableLongStateOf(0L)
    var metrics: Metrics? = null
    var drag: Drag? = null
    var ret: Return? = null
    val clears = mutableListOf<FxClear>()
    val beams = mutableListOf<FxBeam>()
    val texts = mutableListOf<FxText>()
    val rings = mutableListOf<FxRing>()
    val scoreFlights = mutableListOf<ScoreFly>()
    var canvasOrigin = Offset.Zero
    var scoreTarget = Offset(-100f, -100f)
    var flashAt = -10_000L
    var flashColor = Color.White
    private val rnd = kotlin.random.Random(System.nanoTime())
    val bornAt = LongArray(N * N)
    var displayScore by mutableFloatStateOf(0f)
    var scoreBumpAt by mutableLongStateOf(-10_000L)
    var best by mutableIntStateOf(prefs.best)
    var stuckAt by mutableLongStateOf(-1L)
    var showSettings by mutableStateOf(false)
    var combo by mutableIntStateOf(0)
    var movesLeft by mutableIntStateOf(3)
    var beatBest by mutableStateOf(false)
    var muted by mutableStateOf(prefs.muted)
    var music by mutableStateOf(prefs.music)
    var vibrate by mutableStateOf(prefs.vibrate)
    var noAds by mutableStateOf(prefs.noAds)
    var shakeAt = -10_000L
    var shakeAmp = 0f
    var trayAt = -10_000L
    private var seenTray = game.trayVersion
    private var bestAtStart = prefs.best
    private var losePlayed = false
    var hx: Haptics? = null
        set(v) { field = v; v?.enabled = vibrate }

    init {
        sound?.muted = muted
        // a fresh color theme every time the app is opened
        newTheme()
    }

    val gameOverVisible get() = stuckAt >= 0 && now - stuckAt > 900

    fun sfx(s: Sfx, rate: Float = 1f) = sound?.play(s, rate)

    /** Plastic snap: a slight random pitch wobble (about ±5%) so repeated placements never sound robotic. */
    private fun placeRate(): Float = 0.95f + rnd.nextFloat() * 0.1f

    /** Pick a different color theme (board + background). */
    private fun newTheme() {
        var i: Int
        do { i = rnd.nextInt(Themes.all.size) } while (i == prefs.theme && Themes.all.size > 1)
        prefs.theme = i
        Pal.theme = Themes.all[i]
    }

    // ---------------- settings
    fun toggleSound() {
        muted = !muted
        prefs.muted = muted
        sound?.muted = muted
        sfx(Sfx.TAP)
    }

    fun toggleMusic() {
        music = !music
        prefs.music = music
        sound?.setMusic(music)
        sfx(Sfx.TAP)
    }

    fun toggleVibrate() {
        vibrate = !vibrate
        prefs.vibrate = vibrate
        hx?.enabled = vibrate
        if (vibrate) hx?.click()
        sfx(Sfx.TAP)
    }

    /** Test build: toggles freely (so ads can be turned back on). Release: becomes the Play Billing "Remove Ads" purchase. */
    fun toggleNoAds() {
        if (noAds && !BuildConfig.DEBUG) return
        noAds = !noAds
        prefs.noAds = noAds
        sfx(if (noAds) Sfx.FANFARE else Sfx.TAP)
    }

    // ---------------- screens
    /** Home -> game: resume a game in progress, otherwise start a new one. Fresh colors each time. */
    fun play() {
        sfx(Sfx.TAP)
        newTheme()
        if (stuckAt >= 0 || game.score == 0 && game.grid.all { it == 0 }) resetGame(changeTheme = false)
        screen = Screen.Game
    }

    fun goHome() {
        sfx(Sfx.TAP)
        showSettings = false
        screen = Screen.Home
    }

    fun replay() {
        sfx(Sfx.TAP)
        resetGame(changeTheme = true)
        screen = Screen.Game
    }

    private fun resetGame(changeTheme: Boolean) {
        game.reset()
        bornAt.fill(0)
        clears.clear(); beams.clear(); texts.clear(); rings.clear(); scoreFlights.clear()
        displayScore = 0f
        stuckAt = -1
        beatBest = false
        bestAtStart = best
        showSettings = false
        losePlayed = false
        if (changeTheme) newTheme()
        sync()
    }

    // ---------------- frame
    fun tick(t: Long) {
        now = t
        val sIt = scoreFlights.iterator()
        while (sIt.hasNext()) {
            val f = sIt.next()
            if (now - f.t0 >= f.total) { sIt.remove(); scoreBumpAt = now }
        }
        // the counter only counts up when the flying points arrive
        val s = (game.score - scoreFlights.sumOf { it.value }).toFloat()
        displayScore = if (abs(s - displayScore) < 1f) s else displayScore + (s - displayScore) * 0.2f
        if (game.trayVersion != seenTray) { seenTray = game.trayVersion; trayAt = now }
        if (gameOverVisible && !losePlayed) {
            losePlayed = true
            if (beatBest) sfx(Sfx.FANFARE) else sfx(Sfx.LOSE)
            hx?.lose()
        }
        ret?.let { if (now - it.t0 > 180) ret = null }
        clears.removeAll { now - it.t0 > 900 }
        beams.removeAll { now - it.t0 > 500 }
        texts.removeAll { now - it.t0 > 1300 }
        rings.removeAll { now - it.t0 > 700 }
    }

    /** Effective piece position: the piece travels farther than the finger, so short thumb moves reach the whole board. */
    fun eff(d: Drag) = Offset(d.sx + (d.x - d.sx) * 1.4f, d.sy + (d.y - d.sy) * 1.6f)

    /** Best valid spot for the dragged piece: the nearest open spot within about one cell, or null. */
    fun target(d: Drag): Cell? {
        val m = metrics ?: return null
        val p = game.tray[d.slot] ?: return null
        val e = eff(d)
        val f = m.snapF(p, e.x, e.y)
        var best: Cell? = null
        var bestD = 1.05f
        val r0 = floor(f.y).toInt()
        val c0 = floor(f.x).toInt()
        for (r in r0 - 1..r0 + 2) for (c in c0 - 1..c0 + 2) {
            if (!game.canPlace(p, r, c)) continue
            val dist = hypot(r - f.y, c - f.x)
            if (dist < bestD) { bestD = dist; best = Cell(r, c) }
        }
        return best
    }

    fun shake(amountDp: Float) {
        val m = metrics ?: return
        shakeAt = now
        shakeAmp = amountDp * m.dp
    }

    private fun sync() {
        combo = game.combo
        movesLeft = 3 - game.movesSinceClear
        if (game.score > best) {
            if (!beatBest && bestAtStart > 0) {
                metrics?.let { m -> texts.add(FxText("NEW BEST!", m.w / 2, m.cellY(1), Pal.block(2), 34f, now + 250)) }
                sfx(Sfx.FANFARE)
            }
            beatBest = true
            best = game.score
            prefs.best = best
        }
        if (game.isStuck()) {
            if (stuckAt < 0) { stuckAt = now; losePlayed = false }
        } else stuckAt = -1
    }

    fun startDrag(slot: Int, x: Float, y: Float) {
        drag = Drag(slot, x, y, x, y, now)
        ret = null
        sfx(Sfx.PICK)
        hx?.tick()
    }

    fun drop(d: Drag) {
        val m = metrics ?: return
        val p = game.tray[d.slot] ?: return
        val e = eff(d)
        val at = target(d)
        val before = game.score
        val res = if (at != null) game.place(d.slot, at.r, at.c) else null
        if (res == null) {
            ret = Return(d.slot, m.dragTopLeft(p, e.x, e.y), now)
            sfx(Sfx.FAIL)
            return
        }
        res.placed.forEach { bornAt[it.r * N + it.c] = now }
        sfx(Sfx.PLACE, placeRate())
        if (res.lines == 0) {
            hx?.click()
            // small quick "+N" from the grid's center straight to the score
            if (game.score > before) scoreFlights.add(ScoreFly(res.points, m.w / 2, m.w / 2, now, hold = false))
        } else {
            // pitch climbs one semitone per combo step (capped at an octave)
            sfx(Sfx.CLEAR, 2f.pow(((res.combo - 1).coerceIn(0, 12)) / 12f))
            if (res.lines >= 2) sfx(Sfx.BIG)
            if (res.flushLines > 0) sfx(Sfx.FLUSH)
            if (res.allClear) sfx(Sfx.FANFARE)
            if (res.lines >= 2 || res.flushLines > 0 || res.allClear) hx?.big(res.lines + res.flushLines) else hx?.heavy()
            shake((3f + 3f * (res.lines - 1) + if (res.flushLines > 0) 3f else 0f).coerceAtMost(12f))

            val cr = res.placed.map { it.r }.average().toFloat()
            val cc = res.placed.map { it.c }.average().toFloat()
            res.cleared.forEach { (r, c, k) ->
                clears.add(FxClear(r, c, k, now + (hypot(r - cr, c - cc) * 30).toLong()))
            }
            val col = res.cleared.first().third
            res.rows.forEach { beams.add(FxBeam(true, it, col, now)) }
            res.cols.forEach { beams.add(FxBeam(false, it, col, now)) }

            val mx = res.cleared.map { m.cellX(it.second) + m.cell / 2 }.average().toFloat()
            val my = res.cleared.map { m.cellY(it.first) + m.cell / 2 }.average().toFloat()
            rings.add(FxRing(mx, my, col, now + 60, res.lines >= 2 || res.flushLines > 0))
            if (res.lines >= 2 || res.flushLines > 0 || res.allClear) {
                flashAt = now
                flashColor = if (res.flushLines > 0) Pal.flush else Pal.block(col)
            }
            val lines = mutableListOf<Pair<String, Color>>()
            if (res.allClear) lines += "ALL CLEAR!" to Pal.block(2)
            if (res.flushLines > 0) lines += (if (res.flushLines > 1) "COLOR FLUSH ×${res.flushLines}!" else "COLOR FLUSH!") to Pal.flush
            when {
                res.lines >= 4 -> "Unbelievable!"
                res.lines == 3 -> "Amazing!"
                res.lines == 2 -> "Great!"
                else -> null
            }?.let { lines += it to Pal.text }
            if (res.combo >= 2) lines += "Combo ×${res.combo}" to Pal.block(1)
            // words stack above the clear; the points pop at the grid's center, then fly up into the score
            lines.forEachIndexed { i, (t, c) ->
                val rainbow = t.startsWith("COLOR FLUSH") || t == "ALL CLEAR!"
                texts.add(FxText(t, mx, my + (i - lines.size) * 50 * m.dp - 30 * m.dp, c, 36f, now + i * 70L, rainbow))
            }
            scoreFlights.add(ScoreFly(res.points, m.w / 2, m.w / 2, now + 40, hold = true))
        }
        sync()
    }

    /** Revive (once per game). Test build: free. Real build: after a rewarded ad. Clears the fullest 3x3 and deals fitting pieces. */
    fun revive() {
        game.usedBlast = true
        game.blast3x3().forEach { (r, c, k) -> clears.add(FxClear(r, c, k, now + 120)) }
        if (game.isStuck()) game.newTray()
        sfx(Sfx.BOOM)
        shake(14f)
        hx?.boom()
        stuckAt = -1
        losePlayed = false
        sync()
    }
}

/** Color themes: a solid, darkish background with a matching deep grid. A new one each game / app start. */
class Theme(val bg: Color, val grid: Color, val empty: Color, val shadow: Color)

object Themes {
    val all = listOf(
        Theme(Color(0xFF3D45B0), Color(0xFF11144A), Color(0xFF1E236E), Color(0xFF232A86)), // royal
        Theme(Color(0xFF1E7F8E), Color(0xFF08303A), Color(0xFF104A57), Color(0xFF0F5560)), // teal
        Theme(Color(0xFF6A3FB5), Color(0xFF1D0F48), Color(0xFF2C1A6C), Color(0xFF45288A)), // grape
        Theme(Color(0xFFAE4470), Color(0xFF3A0E22), Color(0xFF561937), Color(0xFF7A2A4D)), // berry
        Theme(Color(0xFF2F8F5B), Color(0xFF0B2E1B), Color(0xFF15462B), Color(0xFF1D6440)), // forest
        Theme(Color(0xFF2D72C4), Color(0xFF0B2348), Color(0xFF14366E), Color(0xFF1C4E92)), // ocean
        Theme(Color(0xFFC4633A), Color(0xFF3D170A), Color(0xFF5A2512), Color(0xFF8A3C1E)), // sunset
    )
}
