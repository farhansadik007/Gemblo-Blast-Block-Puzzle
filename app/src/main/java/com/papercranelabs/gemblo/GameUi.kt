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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin
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
    /** Set by the Play Billing "No Ads" purchase (test build: by the No Ads button, free). */
    var noAds: Boolean
        get() = sp.getBoolean("no_ads", false)
        set(v) { sp.edit().putBoolean("no_ads", v).apply() }
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }
    /** Finished games (counted only while ads are on): drives the between-games ads and the No Ads offer. */
    var gamesPlayed: Int
        get() = sp.getInt("games_played", 0)
        set(v) { sp.edit().putInt("games_played", v).apply() }
    /** The game count at which the No Ads offer pops up next (every 5-8 games). */
    var nextOffer: Int
        get() = sp.getInt("next_offer", 0)
        set(v) { sp.edit().putInt("next_offer", v).apply() }
    /** Daily reminder notification on/off, and whether we've asked for the notification permission yet. */
    var reminders: Boolean
        get() = sp.getBoolean("reminders", true)
        set(v) { sp.edit().putBoolean("reminders", v).apply() }
    var notifAsked: Boolean
        get() = sp.getBoolean("notif_asked", false)
        set(v) { sp.edit().putBoolean("notif_asked", v).apply() }
    /** The day (epoch day) of the last game played: no reminder on a day already played. */
    var lastPlayDay: Long
        get() = sp.getLong("last_play_day", -1L)
        set(v) { sp.edit().putLong("last_play_day", v).apply() }
    /** Journey: the next level to play (1-based). Levels below it are passed. */
    var journeyLevel: Int
        get() = sp.getInt("journey_level", 1)
        set(v) { sp.edit().putInt("journey_level", v).apply() }
    /** The game in progress (see Game.save), or null when there's nothing to continue. */
    var savedGame: String?
        get() = sp.getString("saved_game", null)
        set(v) { sp.edit().putString("saved_game", v).apply() }
}

enum class Screen { Home, Game, Journey }
enum class Mode { Classic, Journey }

/**
 * Journey: a straight road of levels going up. Each level starts with random blocks on the board and has a
 * target score. Every level passed adds one piece to a 3x3 story picture; every 9 levels a picture is finished.
 */
object Journey {
    const val PIECES = 9
    fun target(level: Int) = 500 + 150 * (level - 1)
    fun startBlocks(level: Int) = minOf(10 + level, 30)
    /** Which picture (0-based) this level builds. */
    fun picture(level: Int) = (level - 1) / PIECES
    /** Which piece (0..8) of its picture this level adds. */
    fun piece(level: Int) = (level - 1) % PIECES
    /** Each picture's stretch of road has its own color theme. */
    fun theme(level: Int) = Themes.all[picture(level) % Themes.all.size]
}

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
    /** Preferred tray block size; long pieces shrink below this so they never overlap their neighbours. */
    private val trayCellMax = floor(cell * 0.64f)
    private val trayGap = gap * 0.6f

    /** Tray block size for this piece: as big as possible while the piece fits inside its own third of the tray. */
    fun tCell(p: Piece): Float {
        val slotW = w / 3f - 10 * dp
        val slotH = trayH - 14 * dp
        val byW = (slotW - (p.w - 1) * trayGap) / p.w
        val byH = (slotH - (p.h - 1) * trayGap) / p.h
        return floor(minOf(trayCellMax, byW, byH))
    }

    fun tStep(p: Piece) = tCell(p) + trayGap

    fun cellX(c: Int) = pad + c * step
    fun cellY(r: Int) = pad + r * step

    fun hitSlot(x: Float, y: Float): Int? =
        if (y >= trayTop - 8 * dp) (x / (w / 3f)).toInt().coerceIn(0, 2) else null

    fun slotCenter(i: Int) = Offset(w * (i + 0.5f) / 3f, trayTop + trayH / 2f)

    fun trayTopLeft(p: Piece, i: Int): Offset {
        val ctr = slotCenter(i)
        return Offset(ctr.x - (p.w * tStep(p) - trayGap) / 2, ctr.y - (p.h * tStep(p) - trayGap) / 2)
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
    /** Ads + store, provided by MainActivity. Null in previews: everything is free and instant then. */
    interface Host {
        /** Localized No Ads price from Google Play, or null until it loads. */
        val price: String?
        fun showInterstitial()
        fun showRewarded(onReward: () -> Unit)
        /** Opens the Google Play purchase. False if the store isn't available. */
        fun buyNoAds(): Boolean
        /** Asks for the notification permission (Android 13+), once. */
        fun askNotifications()
        /** Turns the daily reminder schedule on or off. */
        fun setReminders(on: Boolean)
    }
    var host: Host? = null

    // ---------------- modes
    var mode by mutableStateOf(Mode.Classic)
        private set
    private val classicGame = game
    private val journeyGame = Game()
    /** The next Journey level to play. */
    var journeyLevel by mutableIntStateOf(prefs.journeyLevel)
        private set
    /** The Journey level on the board right now. */
    var playingLevel by mutableIntStateOf(1)
        private set
    val target get() = Journey.target(playingLevel)
    /** The target was reached: the board stops, then the "Level complete" popup shows. */
    var levelDone by mutableStateOf(false)
        private set
    private var levelDoneAt = -1L
    val levelDoneVisible get() = levelDone && now - levelDoneAt > 900
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
    // score count-up: each gain counts from the old number to the new one, easing out; bigger gains count longer
    private var countFrom = 0f
    private var countTo = 0f
    private var countAt = -10_000L
    private var countDur = 1L
    /** 0..1 while a sizable gain is counting up (peaks mid-count): the score grows and glows gold. */
    val countGlow: Float get() {
        val p = (now - countAt).toFloat() / countDur
        if (p !in 0f..1f) return 0f
        return sin(p * PI.toFloat()) * ((countTo - countFrom) / 300f).coerceIn(0f, 1f)
    }
    var best by mutableIntStateOf(prefs.best)
    var stuckAt by mutableLongStateOf(-1L)
    var showSettings by mutableStateOf(false)
    /** The PaperCrane Labs splash, shown once when the app opens. */
    var showSplash by mutableStateOf(true)
    /** The No Ads offer popup (Home button, settings tile, and on its own every 5-8 games). */
    var showOffer by mutableStateOf(false)
    /** "You're ad-free" popup: tapping the Premium crown (Home) or the Premium tile (settings). */
    var showPremium by mutableStateOf(false)
    /** Journey's album of story pictures, and the one picture shown big (-1 = none). */
    var showAlbum by mutableStateOf(false)
    var albumView by mutableIntStateOf(-1)
    var combo by mutableIntStateOf(0)
    private var hypeSaid = 0
    var movesLeft by mutableIntStateOf(3)
    var beatBest by mutableStateOf(false)
    var muted by mutableStateOf(prefs.muted)
    var music by mutableStateOf(prefs.music)
    var vibrate by mutableStateOf(prefs.vibrate)
    var noAds by mutableStateOf(prefs.noAds)
    var reminders by mutableStateOf(prefs.reminders)
    var shakeAt = -10_000L
    /** Combo border around the grid: glow 0..1 (gold), rainbow mix 0..1, and a flash on every clear. */
    var borderGlow = 0f
        private set
    var borderRainbow = 0f
        private set
    var clearAt = -10_000L
        private set
    private var rainbowUntil = -10_000L
    var shakeAmp = 0f
    var trayAt = -10_000L
    /**
     * Grid intro (every new, resumed or Journey game): a diagonal wave of colored blocks flashes through
     * every cell, and the board's real blocks pop in behind it. Input waits until it's over.
     */
    var introAt = -10_000L
        private set
    val introPlaying get() = now - introAt < INTRO_MS

    private fun startIntro() {
        introAt = now
        for (i in 0 until N * N) bornAt[i] = if (game.grid[i] != 0) now + introDelay(i / N, i % N) + 230 else 0
        sfx(Sfx.WHOOSH)
    }
    private var seenTray = game.trayVersion
    private var bestAtStart = prefs.best
    private var losePlayed = false
    var hx: Haptics? = null
        set(v) { field = v; v?.enabled = vibrate }

    /** Home shows "Continue" when this is true. */
    var canContinue by mutableStateOf(false)
        private set

    init {
        sound?.muted = muted
        // a fresh color theme every time the app is opened
        newTheme()
        // continue where the player left off (even after Android closed the app)
        if (game.load(prefs.savedGame) && game.inProgress()) {
            displayScore = game.score.toFloat()
            combo = game.combo
            movesLeft = 3 - game.movesSinceClear
        } else {
            game.reset()
            prefs.savedGame = null
        }
        canContinue = game.inProgress()
    }

    /** Saves the Classic game in progress (or clears the save once it's over). Called after every change and on pause. */
    fun save() {
        canContinue = classicGame.inProgress()
        prefs.savedGame = if (canContinue) classicGame.save() else null
    }

    val gameOverVisible get() = stuckAt >= 0 && now - stuckAt > 900 && !levelDone

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

    fun toggleReminders() {
        reminders = !reminders
        prefs.reminders = reminders
        host?.setReminders(reminders)
        if (reminders) host?.askNotifications()
        sfx(Sfx.TAP)
    }

    /** Today counts as played (the evening reminder skips today). */
    private fun markPlayed() { prefs.lastPlayDay = java.time.LocalDate.now().toEpochDay() }

    fun toggleVibrate() {
        vibrate = !vibrate
        prefs.vibrate = vibrate
        hx?.enabled = vibrate
        if (vibrate) hx?.click()
        sfx(Sfx.TAP)
    }

    // ---------------- No Ads (premium)
    fun openOffer() {
        sfx(Sfx.TAP)
        showSettings = false
        showOffer = true
    }

    // ---------------- back button
    /** When Back was pressed on Home: a second press within 2 s exits ("Press back again to exit"). */
    var exitHintAt by mutableLongStateOf(-10_000L)
        private set

    /** The phone's Back: closes the top popup, else steps back a screen. Returns false = exit the app. */
    fun back(): Boolean {
        when {
            albumView >= 0 -> closePicture()
            showOffer -> closeOffer()
            showPremium -> closePremium()
            showSettings -> { sfx(Sfx.TAP); showSettings = false }
            showAlbum -> closeAlbum()
            levelDoneVisible -> afterLevel()
            screen == Screen.Game -> goHome()
            screen == Screen.Journey -> backToHome()
            else -> {
                if (now - exitHintAt < 2000) return false
                exitHintAt = now
            }
        }
        return true
    }

    fun openAlbum() { sfx(Sfx.TAP); showAlbum = true }
    fun closeAlbum() { sfx(Sfx.TAP); showAlbum = false }
    fun viewPicture(i: Int) { sfx(Sfx.TAP); albumView = i }
    fun closePicture() { sfx(Sfx.TAP); albumView = -1 }
    /** Road animation: the stretch from level [roadFrom] to the next one fills in gold, starting at [roadAt]. */
    var roadFrom by mutableIntStateOf(0)
        private set
    var roadAt = -10_000L
        private set
    /** 0..1 progress of that fill (1 = done). */
    val roadT get() = ((now - roadAt) / ROAD_MS).coerceIn(0f, 1f)

    /** Pieces earned so far of story picture [i] (0..9). */
    fun piecesOf(i: Int) = (journeyLevel - 1 - i * Journey.PIECES).coerceIn(0, Journey.PIECES)

    fun openPremium() {
        sfx(Sfx.TAP)
        showSettings = false
        showPremium = true
    }

    fun closePremium() {
        sfx(Sfx.TAP)
        showPremium = false
    }

    fun closeOffer() {
        sfx(Sfx.TAP)
        showOffer = false
    }

    /** The price button: Google Play's purchase sheet. The test build (not on Play) grants it for free. */
    fun buyNoAds() {
        sfx(Sfx.TAP)
        val h = host
        if (h == null || !h.buyNoAds()) {
            if (h == null || BuildConfig.DEBUG) grantNoAds() else storeError = now
        }
    }

    /** Set when the store couldn't open (release only): the offer shows a short "try again" note. */
    var storeError by mutableLongStateOf(-10_000L)

    /** The purchase went through (or was restored at start-up). */
    fun grantNoAds() {
        if (noAds) return
        noAds = true
        prefs.noAds = true
        showOffer = false
        sfx(Sfx.FANFARE)
        hx?.boom()
    }

    /** Test build only: the Premium tile turns ads back on, so the free flow can be tested again. */
    fun debugDropNoAds() {
        if (!BuildConfig.DEBUG) return
        noAds = false
        prefs.noAds = false
        showPremium = false
        sfx(Sfx.TAP)
    }

    // ---------------- screens
    /** Home -> game: continue the game in progress, otherwise start a new one. Fresh colors each time. */
    fun play() {
        sfx(Sfx.TAP)
        useClassic()
        newTheme()
        val finished = !game.inProgress() && game.score > 0
        if (!game.inProgress()) resetGame(changeTheme = false)
        screen = Screen.Game
        startIntro()
        markPlayed()
        if (finished) gameFinished()
    }

    /** Settings "Home": a Journey level goes back to the road, Classic to Home. */
    fun goHome() {
        sfx(Sfx.TAP)
        showSettings = false
        if (mode == Mode.Journey && screen == Screen.Game) openJourney(tap = false) else screen = Screen.Home
    }

    /** The Classic game's score (for Home's Continue chip, whatever mode was played last). */
    val classicScore get() = classicGame.score

    /** Home's "new game" button: always a fresh Classic game. */
    fun newClassicGame() {
        useClassic()
        replay()
    }

    fun backToHome() {
        sfx(Sfx.TAP)
        screen = Screen.Home
    }

    /** Back to the Classic game (and its board, score and combo) after Journey. */
    private fun useClassic() {
        if (mode == Mode.Classic) return
        mode = Mode.Classic
        game = classicGame
        clearFx()
        levelDone = false
        displayScore = game.score.toFloat()
        stuckAt = -1
        beatBest = false
        bestAtStart = best
        seenTray = game.trayVersion
        sync()
    }

    private fun clearFx() {
        bornAt.fill(0)
        borderGlow = 0f; borderRainbow = 0f; rainbowUntil = -10_000L; clearAt = -10_000L
        clears.clear(); beams.clear(); texts.clear(); rings.clear(); scoreFlights.clear()
        drag = null; ret = null
    }

    // ---------------- journey
    fun openJourney(tap: Boolean = true) {
        if (tap) sfx(Sfx.TAP)
        showSettings = false
        Pal.theme = Journey.theme(journeyLevel)
        screen = Screen.Journey
    }

    /** Starts (or restarts) the next Journey level: a messy board and a target score. */
    fun startLevel(tap: Boolean = true) {
        if (tap) sfx(Sfx.TAP)
        mode = Mode.Journey
        game = journeyGame
        playingLevel = journeyLevel
        Pal.theme = Journey.theme(playingLevel)
        levelDone = false
        resetGame(changeTheme = false)
        game.seed(Journey.startBlocks(playingLevel))
        sync()
        screen = Screen.Game
        startIntro()
        markPlayed()
    }

    /** "Continue" on the Level complete popup: back up the road (a between-games ad may show here). */
    fun afterLevel() {
        sfx(Sfx.TAP)
        levelDone = false
        // the road fills in gold from the level just passed up to the next stop
        roadFrom = playingLevel
        roadAt = now + 450
        openJourney(tap = false)
        gameFinished()
    }

    fun replay() {
        sfx(Sfx.TAP)
        if (mode == Mode.Journey) {
            val played = game.score > 0
            startLevel(tap = false)
            if (played) gameFinished()
            return
        }
        val finished = game.score > 0
        resetGame(changeTheme = true)
        screen = Screen.Game
        startIntro()
        markPlayed()
        if (finished) gameFinished()
    }

    /**
     * A game just ended and the next one is starting (free players only):
     * - every 5-8 games: the No Ads offer
     * - the first 10 games never show an ad; after that, an ad every 5 games (games 15, 20, 25, ...)
     * The offer wins if both fall on the same game.
     */
    private fun gameFinished() {
        // after the first finished game: a good moment to ask for the daily reminder permission
        if (!prefs.notifAsked && reminders) { prefs.notifAsked = true; host?.askNotifications() }
        if (noAds) return
        val n = prefs.gamesPlayed + 1
        prefs.gamesPlayed = n
        if (prefs.nextOffer == 0) prefs.nextOffer = 5 + rnd.nextInt(4)
        if (n >= prefs.nextOffer) {
            prefs.nextOffer = n + 5 + rnd.nextInt(4)
            showOffer = true
        } else if (n > FREE_GAMES && (n - FREE_GAMES) % AD_EVERY == 0) {
            host?.showInterstitial()
        }
    }

    private fun resetGame(changeTheme: Boolean) {
        game.reset()
        clearFx()
        displayScore = 0f
        stuckAt = -1
        beatBest = false
        bestAtStart = best
        showSettings = false
        losePlayed = false
        if (changeTheme) newTheme()
        sync()
    }

    // ---------------- store screenshots (debug only: adb ... --es showcase board|combo|reveal|journey|album|home --ei theme N)
    private var showcaseStep: Pair<Long, () -> Unit>? = null
    private var showcaseT0 = -1L

    fun showcase(kind: String, theme: Int) {
        if (!BuildConfig.DEBUG) return
        showSplash = false
        fun paint() { if (theme in Themes.all.indices) Pal.theme = Themes.all[theme] }
        // a colorful board with rows 6 and 7 one domino short of clearing
        val board = intArrayOf(
            0, 0, 2, 2, 0, 0, 5, 5,
            0, 3, 2, 2, 0, 4, 5, 5,
            1, 3, 3, 0, 0, 4, 4, 0,
            1, 1, 3, 0, 2, 2, 4, 0,
            5, 1, 0, 0, 2, 3, 3, 1,
            5, 5, 4, 0, 1, 3, 1, 1,
            2, 5, 4, 0, 1, 1, 3, 3,
            2, 2, 4, 0, 5, 5, 1, 3,
        )
        val tray = listOf(
            Piece(listOf(Cell(0, 0), Cell(1, 0)), 4),
            Piece(listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1)), 2),
            Piece(listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1)), 5),
        )
        fun classicBoard(combo: Int) {
            useClassic()
            clearFx()
            game.debugSet(board, 12480, combo, tray)
            displayScore = 12480f
            screen = Screen.Game
            paint()
            sync()
        }
        when (kind) {
            "board" -> classicBoard(0)
            "combo" -> {
                classicBoard(3)
                // place the domino into the gap: two rows clear on combo x4 (rainbow border, hype voice)
                showcaseStep = 2500L to { drop(Drag(0, 0f, 0f, 0f, 0f, now), Cell(6, 3)) }
            }
            "reveal" -> {
                journeyLevel = 5
                startLevel(tap = false)
                paint()
                showcaseStep = 3000L to { game.debugScore(target); sync() }
            }
            "journey" -> { journeyLevel = 14; openJourney(tap = false); paint() }
            "album" -> { journeyLevel = 23; openJourney(tap = false); showAlbum = true; paint() }
            else -> { screen = Screen.Home; paint() }
        }
    }

    // ---------------- frame
    var demoText = false
    private var lastDemo = 0L
    private var clockBase = -1L
    fun tick(t: Long) {
        // debug-only: sample popup words every 1.2 s, to check how they render (adb: --ez demo_text true)
        if (demoText && screen == Screen.Game && now - lastDemo > 1200) metrics?.let { m ->
            lastDemo = now
            texts.add(FxText("Great!", m.w / 2, m.w * 0.45f, Pal.text, 36f, now))
            texts.add(FxText("Combo ×3", m.w / 2, m.w * 0.62f, Pal.block(1), 36f, now))
            texts.add(FxText("COLOR FLUSH!", m.w / 2, m.w * 0.8f, Pal.flush, 36f, now, rainbow = true))
        }
        // app-relative clock: small numbers keep Float time math precise (device uptime in ms is huge,
        // and as a Float it only advanced in ~30 ms steps, which made every wave animation stutter)
        if (clockBase < 0) clockBase = t - 10_000
        now = t - clockBase
        showcaseStep?.let { (wait, step) ->
            if (showcaseT0 < 0) showcaseT0 = now   // the delay counts from the first real frame
            if (now - showcaseT0 >= wait && metrics != null) { showcaseStep = null; step() }
        }
        val sIt = scoreFlights.iterator()
        while (sIt.hasNext()) {
            val f = sIt.next()
            if (now - f.t0 >= f.total) { sIt.remove(); scoreBumpAt = now }
        }
        // the counter only counts up when the flying points arrive
        val s = (game.score - scoreFlights.sumOf { it.value }).toFloat()
        if (s != countTo) {
            if (s < countTo || s < displayScore) {
                // new game / switched mode: no counting down, just show it
                displayScore = s; countFrom = s; countTo = s; countAt = -10_000L
            } else {
                countFrom = displayScore
                countTo = s
                countAt = now
                val gain = s - countFrom
                countDur = (260f + 230f * log2(1f + gain / 25f)).coerceIn(260f, 1300f).toLong()
            }
        }
        if (displayScore != countTo) {
            val p = ((now - countAt).toFloat() / countDur).coerceIn(0f, 1f)
            val e = 1f - (1f - p) * (1f - p) * (1f - p)
            displayScore = if (p >= 1f) countTo else countFrom + (countTo - countFrom) * e
            // the number lands: a bounce
            if (p >= 1f && countTo - countFrom >= 20f) scoreBumpAt = now
        }
        // combo border: eases toward its target so it never pops on or off
        val glowTarget = if (combo >= 2) (0.55f + 0.08f * (combo - 2)).coerceAtMost(0.9f) else 0f
        borderGlow += (glowTarget - borderGlow) * 0.07f
        val rainbowTarget = if (combo >= 2 || now < rainbowUntil) 1f else 0f   // any live combo = rainbow border
        borderRainbow += (rainbowTarget - borderRainbow) * 0.06f
        if (game.trayVersion != seenTray) {
            seenTray = game.trayVersion
            trayAt = maxOf(now, introAt + INTRO_TRAY)   // after the grid intro, if one is playing
            if (screen != Screen.Home) sfx(Sfx.WHOOSH)   // fresh pieces slide into the tray
        }
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
        if (mode == Mode.Classic && game.score > best) {
            if (!beatBest && bestAtStart > 0) {
                metrics?.let { m -> texts.add(FxText("NEW BEST!", m.w / 2, m.cellY(1), Pal.block(2), 34f, now + 250)) }
                sfx(Sfx.FANFARE)
            }
            beatBest = true
            best = game.score
            prefs.best = best
        }
        if (mode == Mode.Journey && !levelDone && game.score >= target) {
            levelDone = true
            levelDoneAt = now
            journeyLevel = playingLevel + 1
            prefs.journeyLevel = journeyLevel
            metrics?.let { m -> texts.add(FxText("LEVEL COMPLETE!", m.w / 2, m.cellY(2), Pal.block(2), 36f, now + 200, rainbow = true)) }
            sfx(Sfx.FANFARE)   // the recorded "Level Complete" (the level-up sound plays with the picture reveal)
            hx?.boom()
        }
        if (!levelDone && game.isStuck()) {
            if (stuckAt < 0) { stuckAt = now; losePlayed = false }
        } else stuckAt = -1
        save()
    }

    fun startDrag(slot: Int, x: Float, y: Float) {
        if (levelDone || introPlaying) return
        drag = Drag(slot, x, y, x, y, now)
        ret = null
        sfx(Sfx.PICK)
        hx?.tick()
    }

    fun drop(d: Drag, forced: Cell? = null) {
        val m = metrics ?: return
        val p = game.tray[d.slot] ?: return
        val e = eff(d)
        val at = forced ?: target(d)
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
            clearAt = now
            if (res.combo >= 4 || res.lines >= 3 || res.flushLines > 0 || res.allClear) rainbowUntil = now + 2600
            // recorded clear sounds, played as recorded (no pitch shifting): a big moment (2+ lines or combo x4+)
            // gets the bigger one instead of the normal one
            val bigHit = res.lines >= 2 || res.combo >= 4
            sfx(if (bigHit) Sfx.BIG else Sfx.CLEAR)
            // the combo plays a melody: each step rings the next note of a pentatonic scale (do re mi sol la do),
            // a big hit adds a harmony note, and combo x3+ adds a sparkle on top
            val step = (res.combo - 1).coerceIn(0, COMBO_NOTES.size - 1)
            sfx(Sfx.CHIME, COMBO_NOTES[step])
            if (bigHit) sfx(Sfx.CHIME, COMBO_NOTES[(step + 2).coerceAtMost(COMBO_NOTES.size - 1)] * (if (step + 2 >= COMBO_NOTES.size) 0.75f else 1f))
            if (res.combo >= 3) sfx(Sfx.SHIMMER)
            // hype level 1..4 (Excellent, Amazing, Crazy, Insane) from the combo streak or a big multi-line clear.
            // The voice speaks (and its word shows) when a new level is reached, or on any 2+ line clear.
            val hype = maxOf(
                when { res.combo >= 6 -> 4; res.combo >= 4 -> 3; res.combo == 3 -> 2; res.combo == 2 -> 1; else -> 0 },
                when { res.lines >= 5 || res.allClear -> 4; res.lines == 4 -> 3; res.lines == 3 -> 2; res.lines == 2 -> 1; else -> 0 },
            )
            if (res.combo <= 1) hypeSaid = 0
            val hypeWord = if (hype > 0 && (hype > hypeSaid || res.lines >= 2)) {
                hypeSaid = maxOf(hypeSaid, hype)
                sfx(HYPE_VOICES[hype - 1])
                HYPE_WORDS[hype - 1]
            } else null
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
            hypeWord?.let { lines += it to Pal.text }
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

    /** The Revive button: Premium revives straight away; free players watch a rewarded video first. */
    fun reviveTapped() {
        if (game.usedBlast) return
        sfx(Sfx.TAP)
        val h = host
        if (noAds || h == null) revive() else h.showRewarded { if (stuckAt >= 0 && !game.usedBlast) revive() }
    }

    /** Revive (once per game): clears the fullest 3x3 and deals fitting pieces. */
    private fun revive() {
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

private const val FREE_GAMES = 10
/** Combo notes as playback rates of the C6 bell: C, D, E, G, A, C (major pentatonic, always sounds sweet). */
private val COMBO_NOTES = floatArrayOf(1f, 1.1225f, 1.2599f, 1.4983f, 1.6818f, 2f)
private const val ROAD_MS = 950f
/** Grid intro timing (ms): each cell starts INTRO_STEP later per diagonal, lives INTRO_CELL; the tray slides in at INTRO_TRAY. */
const val INTRO_STEP = 34
const val INTRO_CELL = 430
const val INTRO_MS = 14 * INTRO_STEP + INTRO_CELL
const val INTRO_TRAY = 420L
fun introDelay(r: Int, c: Int) = (r + c) * INTRO_STEP
private const val AD_EVERY = 5

private val HYPE_VOICES = listOf(Sfx.EXCELLENT, Sfx.AMAZING, Sfx.CRAZY, Sfx.INSANE)
private val HYPE_WORDS = listOf("Excellent!", "Amazing!", "Crazy!", "Insane!")
