package com.papercranelabs.gemblo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import kotlin.math.sin

// ================= press bounce (from Zapblob) =================
@Composable
fun rememberSquash(pressed: Boolean): Animatable<Float, AnimationVector1D> {
    val a = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) a.animateTo(0.88f, spring(dampingRatio = 1f, stiffness = 1800f))
        else a.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 520f))
    }
    return a
}

/** Clickable with a squash-and-bounce. */
fun Modifier.juicy(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a = rememberSquash(pressed)
    val scope = rememberCoroutineScope()
    this
        .graphicsLayer { scaleX = a.value; scaleY = a.value }
        .clickable(src, null, enabled, role = Role.Button) {
            scope.launch {
                if (a.value > 0.94f) a.snapTo(0.9f)
                a.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 520f))
            }
            onClick()
        }
}

// ================= flying coins (time-based port of Zapblob's CoinFx) =================
class CoinFly(
    val sx: Float, val sy: Float, val mx: Float, val my: Float,
    val t0: Long, val delay: Int, val dur: Int, val value: Int, val spin: Float,
)

/** Position and scale of a coin at [now] (root px), or null if it hasn't launched yet. */
fun CoinFly.place(now: Long, target: Offset): Triple<Float, Float, Float>? {
    val u = (now - t0 - delay).toFloat() / dur
    if (u < 0f) return null
    val split = 0.32f
    return if (u < split) {
        val k = u / split
        val e = 1 - (1 - k) * (1 - k)
        Triple(sx + (mx - sx) * e, sy + (my - sy) * e, 0.4f + 0.8f * e)
    } else {
        val k = ((u - split) / (1 - split)).coerceAtMost(1f)
        val e = k * k
        val bend = sin(k * 3.1416f) * 60f * spin
        Triple(mx + (target.x - mx) * e + bend, my + (target.y - my) * e, 1.2f - 0.45f * e)
    }
}
