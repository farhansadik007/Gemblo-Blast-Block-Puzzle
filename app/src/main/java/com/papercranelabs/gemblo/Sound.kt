package com.papercranelabs.gemblo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

enum class Sfx { PICK, PLACE, CLEAR, BIG, FLUSH, FANFARE, FAIL, HAMMER, WHOOSH, BOOM, LOSE, TAP, COIN,
    /** hype voices, one per level (recorded only) */
    EXCELLENT, AMAZING, CRAZY, INSANE, LEVELUP, CHIME, SHIMMER }

/**
 * Procedural sound effects: synthesized at first launch, cached as tiny WAVs and played
 * through a low-latency SoundPool. No audio files to license.
 */
class SoundFx(ctx: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        )
        .build()
    private val ids = IntArray(Sfx.entries.size)
    //                              PICK   PLACE  CLEAR  BIG    FLUSH  FANF   FAIL   HAMMER WHOOSH BOOM   LOSE   TAP    COIN   (voices x4)
    private val vol = floatArrayOf(0.55f, 0.65f, 0.70f, 0.70f, 0.70f, 0.70f, 0.45f, 0.80f, 0.45f, 0.90f, 0.55f, 0.40f, 0.35f, 0.95f, 0.95f, 0.95f, 0.95f, 0.9f, 0.6f, 0.45f)
    var muted = false

    /** Recorded sounds (res/raw) that replace the synthesized ones. Sources and licenses: licenses/SOUNDS.md. */
    private val recorded = mapOf(
        Sfx.PICK to R.raw.sfx_pick,
        Sfx.LOSE to R.raw.sfx_lose,
        Sfx.WHOOSH to R.raw.sfx_whoosh,
        Sfx.EXCELLENT to R.raw.voice_excellent,
        Sfx.AMAZING to R.raw.voice_amazing,
        Sfx.CRAZY to R.raw.voice_crazy,
        Sfx.CLEAR to R.raw.sfx_clear,
        Sfx.BIG to R.raw.sfx_big,
        Sfx.FANFARE to R.raw.sfx_win,
        Sfx.LEVELUP to R.raw.sfx_levelup,
        Sfx.INSANE to R.raw.voice_insane,
    )

    init {
        val dir = File(ctx.cacheDir, "sfx").apply { mkdirs() }
        dir.listFiles()?.filter { !it.name.endsWith("_v8.wav") && it.name != "music_v8.wav" }?.forEach { it.delete() }
        for (s in Sfx.entries) {
            recorded[s]?.let { ids[s.ordinal] = pool.load(ctx, it, 1); continue }
            val f = File(dir, "${s.name.lowercase()}_v8.wav")
            if (!f.exists()) writeWav(f, synth(s))
            ids[s.ordinal] = pool.load(f.path, 1)
        }
    }

    fun play(s: Sfx, rate: Float = 1f) {
        if (muted) return
        val v = vol[s.ordinal]
        pool.play(ids[s.ordinal], v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    // ---------------- background music (a soft procedural loop, generated once off the main thread)
    private var player: MediaPlayer? = null
    private var musicOn = false
    private var paused = false
    private val main = Handler(Looper.getMainLooper())

    init {
        val f = File(File(ctx.cacheDir, "sfx"), "music_v8.wav")
        Thread {
            if (!f.exists()) writeWav(f, synthMusic())
            main.post {
                player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    setDataSource(f.path)
                    isLooping = true
                    setVolume(0.22f, 0.22f)
                    prepare()
                }
                applyMusic()
            }
        }.start()
    }

    private fun applyMusic() {
        val p = player ?: return
        if (musicOn && !paused) { if (!p.isPlaying) p.start() } else if (p.isPlaying) p.pause()
    }

    fun setMusic(on: Boolean) { musicOn = on; applyMusic() }
    fun pauseMusic() { paused = true; applyMusic() }
    fun resumeMusic() { paused = false; applyMusic() }

    fun release() {
        pool.release()
        player?.release()
        player = null
    }

    private companion object {
        const val SR = 44100
        const val TAU = 2 * PI

        fun sine(ph: Double) = sin(ph)
        fun square(ph: Double, duty: Double = 0.5) = if ((ph / TAU) % 1.0 < duty) 1.0 else -1.0
        fun tri(ph: Double) = 2 / PI * asin(sin(ph))
        fun drive(x: Double, k: Double) = tanh(k * x) / tanh(k)

        fun osc(sec: Double, freq: (Double, Double) -> Double, wave: (Double) -> Double, env: (Double, Double) -> Double): FloatArray {
            val n = (sec * SR).toInt()
            var ph = 0.0
            return FloatArray(n) { i ->
                val t = i.toDouble() / SR
                val p = i.toDouble() / n
                ph += TAU * freq(t, p) / SR
                (wave(ph) * env(t, p)).toFloat()
            }
        }

        fun notes(seq: List<Pair<Double, Double>>, wave: (Double) -> Double, amp: Double, decay: Double, vib: Boolean = false): FloatArray {
            val total = seq.sumOf { it.second }
            val out = FloatArray((total * SR).toInt() + 1)
            var start = 0.0
            for ((idx, nl) in seq.withIndex()) {
                val (f, len) = nl
                val last = idx == seq.size - 1
                var ph = 0.0
                val n = (len * SR).toInt()
                for (i in 0 until n) {
                    val t = i.toDouble() / SR
                    val fv = if (vib && last && t > 0.06) f * (1 + 0.012 * sin(TAU * 6 * t)) else f
                    ph += TAU * fv / SR
                    val env = exp(-t * decay) * (if (i < 80) i / 80.0 else 1.0)
                    val k = ((start * SR).toInt() + i).coerceAtMost(out.size - 1)
                    out[k] += (wave(ph) * env * amp).toFloat()
                }
                start += len
            }
            return out
        }

        fun noise(sec: Double, seed: Int, env: (Double, Double) -> Double, hp: Boolean = false, lp: Double = 1.0): FloatArray {
            val r = Random(seed)
            val n = (sec * SR).toInt()
            var prev = 0.0
            var low = 0.0
            return FloatArray(n) { i ->
                val t = i.toDouble() / SR
                val p = i.toDouble() / n
                val w = r.nextDouble() * 2 - 1
                low += (w - low) * lp
                val v = if (hp) low - prev else low
                prev = low
                (v * env(t, p)).toFloat()
            }
        }

        fun mix(vararg parts: FloatArray): FloatArray {
            val out = FloatArray(parts.maxOf { it.size })
            for (a in parts) for (i in a.indices) out[i] += a[i]
            return out
        }

        fun FloatArray.driven(k: Double) = FloatArray(size) { drive(this[it].toDouble(), k).toFloat() }

        const val C5 = 523.25; const val E5 = 659.25; const val G5 = 783.99
        const val C6 = 1046.5; const val D6 = 1174.66; const val E6 = 1318.5; const val FS6 = 1479.98
        const val G6 = 1568.0; const val GS6 = 1661.22; const val AS6 = 1864.66; const val C7 = 2093.0; const val E7 = 2637.02
        const val E4 = 329.63; const val F4 = 349.23; const val FS4 = 369.99; const val G4 = 392.0

        fun synth(s: Sfx): FloatArray = when (s) {
            // tiny bright blip when a piece is lifted
            Sfx.PICK -> osc(0.05, { _, p -> 1100 + 500 * p }, ::sine) { t, _ -> 0.4 * exp(-t * 70) }

            // block landing: a satisfying plastic SNAP. A crisp contact tick, a short hollow plastic-shell
            // resonance (bright inharmonic partials + a hollow mid body), a soft low thud, then a tiny
            // second "settle" click ~14 ms later, like a tile clicking into place. No distortion.
            Sfx.PLACE -> {
                fun att(t: Double) = (t / 0.0008).coerceAtMost(1.0)
                val first = mix(
                    noise(0.008, 21, { t, _ -> 0.55 * exp(-t * 850) }, hp = true, lp = 0.65),
                    osc(0.09, { _, _ -> 2150.0 }, ::sine) { t, _ -> 0.26 * exp(-t * 68) * att(t) },
                    osc(0.05, { _, _ -> 3165.0 }, ::sine) { t, _ -> 0.11 * exp(-t * 115) * att(t) },
                    osc(0.11, { _, _ -> 690.0 }, ::tri) { t, _ -> 0.26 * exp(-t * 46) * att(t) },
                    osc(0.08, { _, p -> 190 * 0.85.pow(p) }, ::sine) { t, _ -> 0.3 * exp(-t * 55) * att(t) },
                )
                val settle = FloatArray((0.014 * SR).toInt()) + mix(
                    noise(0.005, 22, { t, _ -> 0.28 * exp(-t * 1100) }, hp = true, lp = 0.7),
                    osc(0.04, { _, _ -> 2480.0 }, ::sine) { t, _ -> 0.1 * exp(-t * 140) * att(t) },
                )
                mix(first, settle)
            }

            // line clear: rising glassy pop + quick chime + crunchy shards
            // line clear: a soft rising whoosh-ding (kept round: sines + triangles, a little air, gentle drive)
            Sfx.CLEAR -> mix(
                osc(0.2, { _, p -> 420 + 700 * (1 - (1 - p).pow(3)) }, ::sine) { t, _ -> 0.5 * exp(-t * 18) },
                notes(listOf(C5 to 0.035, E5 to 0.035, G5 to 0.22), { tri(it) * 0.5 + sine(it) * 0.5 }, 0.24, 9.0),
                noise(0.1, 7, { t, _ -> 0.16 * exp(-t * 40) }, hp = true, lp = 0.45),
            ).driven(1.25)

            // multi-line: sparkling arpeggio up
            Sfx.BIG -> mix(
                notes(listOf(C5 to 0.045, E5 to 0.045, G5 to 0.045, C6 to 0.045, E6 to 0.32), { tri(it) * 0.6 + sine(it) * 0.4 }, 0.22, 6.0, vib = true),
                noise(0.25, 9, { _, p -> 0.07 * (1 - p) }, hp = true, lp = 0.35),
            ).driven(1.15)

            // color flush: magical whole-tone run + bell
            Sfx.FLUSH -> mix(
                notes(listOf(C6 to 0.03, D6 to 0.03, E6 to 0.03, FS6 to 0.03, GS6 to 0.03, AS6 to 0.03, C7 to 0.4), { tri(it) * 0.7 + sine(it) * 0.3 }, 0.32, 5.0, vib = true),
                osc(0.6, { _, _ -> C7 * 1.5 }, ::sine) { t, _ -> if (t < 0.18) 0.0 else 0.12 * exp(-(t - 0.18) * 6) },
            ).driven(1.3)

            // all clear / new best fanfare
            Sfx.FANFARE -> mix(
                notes(listOf(G4 to 0.09, C5 to 0.09, E5 to 0.09, G5 to 0.12, E5 to 0.09, G5 to 0.5), { tri(it) * 0.6 + sine(it) * 0.4 }, 0.26, 3.0, vib = true),
                notes(listOf(C5 / 4 to 0.27, G4 / 2 to 0.21, C5 / 2 to 0.5), ::tri, 0.35, 2.5),
            ).driven(1.15)

            // bad drop: soft low thud
            Sfx.FAIL -> osc(0.14, { _, p -> 170 * 0.6.pow(p) }, ::tri) { t, _ -> 0.5 * exp(-t * 22) }

            // hammer smash: thump + crunch
            Sfx.HAMMER -> mix(
                osc(0.25, { t, _ -> 80 + 220 * exp(-t * 30) }, ::sine) { t, _ -> 0.9 * exp(-t * 13) },
                noise(0.2, 11, { t, _ -> 0.6 * exp(-t * 28) }, lp = 0.3),
                noise(0.06, 12, { t, _ -> 0.35 * exp(-t * 90) }, hp = true),
            ).driven(2.5)

            // whoosh when a fresh tray of pieces slides in (replaced by res/raw/sfx_whoosh)
            Sfx.WHOOSH -> mix(
                noise(0.26, 13, { _, p -> 0.5 * sin(PI * p) }, lp = 0.12),
                osc(0.26, { _, p -> 300 + 700 * p }, ::tri) { _, p -> 0.12 * sin(PI * p) },
            ).driven(1.3)

            // 3x3 blast continue: big boom
            Sfx.BOOM -> mix(
                osc(0.6, { t, _ -> 38 + 150 * exp(-t * 22) }, ::sine) { t, _ -> 1.0 * exp(-t * 6) },
                noise(0.6, 44, { t, _ -> 0.9 * exp(-t * 7) }, lp = 0.06),
                noise(0.05, 45, { t, _ -> 0.4 * exp(-t * 90) }, hp = true),
            ).driven(3.0)

            // game over: descending wah
            Sfx.LOSE -> notes(
                listOf(G4 to 0.2, FS4 to 0.2, F4 to 0.2, E4 to 0.5),
                { square(it, 0.5) * 0.6 + tri(it) * 0.4 }, 0.3, 2.2, vib = true,
            ).driven(1.3)

            // coin landing in the counter: bright E6 -> B6 blip
            Sfx.COIN -> notes(listOf(E6 to 0.04, E6 * 1.5 to 0.14), { square(it, 0.25) }, 0.22, 16.0).driven(1.3)

            // voices are recorded files only
            Sfx.EXCELLENT, Sfx.AMAZING, Sfx.CRAZY, Sfx.INSANE, Sfx.LEVELUP -> FloatArray(1)

            // combo note: a soft, pure bell at C6 (pitched up the pentatonic scale per combo step, so a streak plays a melody)
            Sfx.CHIME -> mix(
                osc(1.3, { _, _ -> C6 }, ::sine) { t, _ -> 0.55 * exp(-t * 3.0) * (t / 0.003).coerceAtMost(1.0) },
                osc(1.0, { _, _ -> C6 * 2.0 }, ::sine) { t, _ -> 0.13 * exp(-t * 5.0) * (t / 0.003).coerceAtMost(1.0) },
                osc(0.5, { _, _ -> C6 * 2.76 }, ::sine) { t, _ -> 0.07 * exp(-t * 10.0) * (t / 0.002).coerceAtMost(1.0) },
            )

            // combo x3+: a gentle sparkle run on top
            Sfx.SHIMMER -> mix(
                notes(listOf(E6 to 0.045, G6 to 0.045, C7 to 0.045, E6 * 2 to 0.35), ::sine, 0.32, 7.0),
                noise(0.35, 13, { _, p -> 0.03 * (1 - p) }, hp = true, lp = 0.3),
            )

            // UI tap
            Sfx.TAP -> mix(
                osc(0.07, { _, p -> 980 - 560 * p }, ::sine) { _, p -> 0.6 * (1 - p).pow(2.5) },
                noise(0.012, 5, { _, p -> 0.15 * (1 - p) }, hp = true, lp = 0.5),
            )
        }

        /**
         * Calm piano loop: 16 bars at 72 bpm in C major (about 53 s), seamless.
         * Left hand: a slow flowing arpeggio over a soft bass note. Right hand: a sparse, low, singing melody.
         * Piano voice: a soft felt piano: few partials (dark, never shrill), a gentle 12 ms attack, two detuned
         * strings on melody notes, and a soft three-tap room echo.
         */
        fun synthMusic(): FloatArray {
            val beat = 60.0 / 72.0
            val bar = 4 * beat
            val bars = 16
            val out = FloatArray((bars * bar * SR).toInt())
            fun hz(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)

            fun piano(start: Double, len: Double, m: Int, amp: Double, partials: Int, strings: Int) {
                val f = hz(m)
                val s0 = (start * SR).toInt()
                val n = (minOf(len + 1.2, 4.5) * SR).toInt()
                val rel = (len * SR).toInt()
                val d1 = 0.7 + f / 900.0
                for (st in 0 until strings) {
                    val det = if (strings == 1) 1.0 else if (st == 0) 0.9997 else 1.0003
                    for (k in 1..partials) {
                        val fk = f * det * k * kotlin.math.sqrt(1 + 0.0003 * k * k)
                        if (fk > 3800) break
                        val ak = amp / strings / k.toDouble().pow(1.9)
                        val dk = d1 * (1 + 0.6 * (k - 1))
                        val w = TAU * fk / SR
                        val cr = kotlin.math.cos(w); val sr = sin(w)
                        var sv = 0.0; var cv = 1.0
                        for (i in 0 until n) {
                            val t = i.toDouble() / SR
                            var env = exp(-t * dk) * (t / 0.012).coerceAtMost(1.0)
                            if (i > rel) env *= exp(-(i - rel).toDouble() / SR * 4)  // key released, slow damper
                            if (i > SR / 50 && env < 0.0003) break
                            out[(s0 + i) % out.size] += (sv * env * ak).toFloat()
                            val ns = sv * cr + cv * sr
                            cv = cv * cr - sv * sr
                            sv = ns
                        }
                    }
                }
            }

            // chords: root (MIDI), minor?  C Am F G | C Am F G | F Em Dm G | C Am F C
            val chords = listOf(
                48 to false, 45 to true, 41 to false, 43 to false, 48 to false, 45 to true, 41 to false, 43 to false,
                41 to false, 40 to true, 50 to true, 43 to false, 48 to false, 45 to true, 41 to false, 48 to false,
            )
            // left hand: soft bass on beat 1, then a slow rolling arpeggio in eighths (root, fifth, octave, third, fifth...)
            for ((b, ch) in chords.withIndex()) {
                val (r, minor) = ch
                val third = if (minor) 15 else 16
                val t0 = b * bar
                piano(t0, bar * 0.95, r - 12, 0.085, 3, 1)
                val arp = listOf(0, 7, 12, third, 19, third, 12, 7)
                for ((i, iv) in arp.withIndex()) piano(t0 + i * beat / 2, beat * 1.4, r + iv, 0.042, 3, 1)
            }

            // melody: bar to list of (start beat, beats, MIDI); sparse and singing, held long
            val mel = mapOf(
                0 to listOf(Triple(0.0, 2.0, 76), Triple(2.0, 2.0, 74)),
                1 to listOf(Triple(0.0, 3.0, 72), Triple(3.0, 1.0, 71)),
                2 to listOf(Triple(0.0, 2.0, 69), Triple(2.0, 2.0, 72)),
                3 to listOf(Triple(0.0, 4.0, 74)),
                4 to listOf(Triple(0.0, 1.5, 76), Triple(1.5, 0.5, 79), Triple(2.0, 2.0, 76)),
                5 to listOf(Triple(0.0, 2.0, 72), Triple(2.0, 2.0, 76)),
                6 to listOf(Triple(0.0, 2.0, 77), Triple(2.0, 1.0, 76), Triple(3.0, 1.0, 72)),
                7 to listOf(Triple(0.0, 4.0, 74)),
                8 to listOf(Triple(0.0, 2.0, 81), Triple(2.0, 2.0, 79)),
                9 to listOf(Triple(0.0, 2.0, 79), Triple(2.0, 2.0, 76)),
                10 to listOf(Triple(0.0, 2.0, 77), Triple(2.0, 2.0, 74)),
                11 to listOf(Triple(0.0, 3.0, 74), Triple(3.0, 1.0, 71)),
                12 to listOf(Triple(0.0, 2.0, 72), Triple(2.0, 2.0, 76)),
                13 to listOf(Triple(0.0, 2.0, 79), Triple(2.0, 2.0, 76)),
                14 to listOf(Triple(0.0, 2.0, 74), Triple(2.0, 2.0, 72)),
                15 to listOf(Triple(0.0, 4.0, 72)),
            )
            for ((b, notes) in mel) for ((st, len, m) in notes) {
                piano(b * bar + st * beat, len * beat * 0.95, m, 0.12, 4, 2)
            }

            // soft room: three echo taps, wrapped so the loop stays seamless
            val dry = out.copyOf()
            for ((delay, g) in listOf(0.21 to 0.18, 0.37 to 0.11, 0.59 to 0.06)) {
                val d = (delay * SR).toInt()
                for (i in dry.indices) out[(i + d) % out.size] += dry[i] * g.toFloat()
            }
            return out
        }

        fun writeWav(f: File, src: FloatArray) {
            val data = src.copyOf()
            val peak = data.maxOf { abs(it) }.coerceAtLeast(1e-6f)
            if (peak > 0.98f) for (i in data.indices) data[i] *= 0.98f / peak
            val fade = (SR * 0.004).toInt().coerceAtMost(data.size)
            for (i in 0 until fade) data[data.size - 1 - i] *= i.toFloat() / fade
            val pcm = ByteBuffer.allocate(44 + data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.put("RIFF".toByteArray()).putInt(36 + data.size * 2).put("WAVE".toByteArray())
            pcm.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SR).putInt(SR * 2).putShort(2).putShort(16)
            pcm.put("data".toByteArray()).putInt(data.size * 2)
            for (v in data) pcm.putShort((v.coerceIn(-1f, 1f) * 32000).toInt().toShort())
            FileOutputStream(f).use { it.write(pcm.array()) }
        }
    }
}
