package com.papercranelabs.gemblo

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Drives the vibration motor directly, so feedback works even when system touch haptics are off. */
class Haptics(ctx: Context) {
    private val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    var enabled = true

    private fun predefined(id: Int, fallbackMs: Long, amp: Int) {
        val v = vib ?: return
        if (!enabled || !v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(id))
        else v.vibrate(VibrationEffect.createOneShot(fallbackMs, amp))
    }

    private fun wave(timings: LongArray, amps: IntArray) {
        val v = vib ?: return
        if (!enabled || !v.hasVibrator()) return
        if (v.hasAmplitudeControl()) v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        else v.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    /** Piece lifted, coin landed. */
    fun tick() = predefined(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_TICK else 0, 8, 60)
    /** Piece placed. */
    fun click() = predefined(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_CLICK else 0, 15, 140)
    /** One line cleared. */
    fun heavy() = predefined(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_HEAVY_CLICK else 0, 25, 220)
    /** Multi-line clear / Color Flush: a rumble that grows with the size of the clear. */
    fun big(lines: Int) {
        val n = lines.coerceIn(2, 5)
        val t = LongArray(n * 2) { if (it % 2 == 0) 25L else 35L }
        val a = IntArray(n * 2) { if (it % 2 == 0) 0 else (150 + it * 20).coerceAtMost(255) }
        wave(t, a)
    }
    /** Hammer smash, 3x3 blast. */
    fun boom() = wave(longArrayOf(0, 60, 30, 90), intArrayOf(0, 255, 0, 180))
    /** Game over. */
    fun lose() = wave(longArrayOf(0, 120, 80, 120, 80, 260), intArrayOf(0, 160, 0, 120, 0, 90))
}
