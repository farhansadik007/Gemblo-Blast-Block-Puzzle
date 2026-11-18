package com.papercranelabs.gemblo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.floor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papercranelabs.gemblo.ads.AdManager
import com.papercranelabs.gemblo.ads.BannerAd
import com.papercranelabs.gemblo.game.Cell
import com.papercranelabs.gemblo.game.N
import com.papercranelabs.gemblo.game.Piece
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

object Pal {
    /** Current color theme: solid darkish background + matching deep grid. Changes every game / app start. */
    var theme by mutableStateOf(Themes.all[0])
    val bg get() = theme.bg
    val grid get() = theme.grid
    val empty get() = theme.empty
    val scoreShadow get() = theme.shadow
    val pill = Color(0x2EFFFFFF)
    val text = Color(0xFFFFFFFF)
    val muted = Color(0xFFE4E7FF)
    val ink = Color(0xFF2A1B3D)
    val inkMuted = Color(0xFF5A4A75)
    val flush = Color(0xFFFF7AD9)
    val dead = Color(0xFF9A93C4)
    val confetti = listOf(Color(0xFFFF2D46), Color(0xFFFFC107), Color(0xFF14D263), Color(0xFF168BFF), Color(0xFF9A4DFF), Color(0xFFFF4FC3))
    private val blocks = listOf(
        Color(0xFFFF2D46), Color(0xFFFFC107), Color(0xFF14D263), Color(0xFF168BFF), Color(0xFF9A4DFF),
    )
    fun block(k: Int) = blocks[(k - 1).coerceIn(0, blocks.size - 1)]
}

val Fredoka = FontFamily(
    Font(R.font.fredoka, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.fredoka, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(650))),
    Font(R.font.fredoka, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.fredoka, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

fun label(size: Int, color: Color = Pal.text, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(textMotion = TextMotion.Animated, fontFamily = Fredoka, fontWeight = weight, fontSize = size.sp, color = color)

private fun fmt(n: Int) = "%,d".format(n)

@Composable
fun GameScreen(ui: GameUi, ads: AdManager) {
    val density = LocalDensity.current.density
    val tm = rememberTextMeasurer()
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { ui.tick(it / 1_000_000) }
    }
    val bg by animateColorAsState(Pal.bg, tween(500), label = "bg")
    Box(Modifier.fillMaxSize().background(bg).then(if (ui.screen == Screen.Home) Modifier.grain() else Modifier)) {
      if (ui.screen == Screen.Home) {
        HomeScreen(ui)
      } else {
      Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(ui)
            Spacer(Modifier.height(10.dp))
            // combo "fever": 0 = calm, 1 = full dance (fades in/out smoothly)
            val fever by animateFloatAsState(
                if (ui.combo >= 2) (0.55f + 0.15f * (ui.combo - 2)).coerceAtMost(1f) else 0f,
                tween(450), label = "fever",
            )
            // fixed-size stage: the aura's center never moves when the score gains digits
            Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                if (fever > 0.01f) ComboAura(ui, fever, Modifier.matchParentSize())
                FixedDigits(
                    fmt(ui.displayScore.roundToInt()),
                    modifier = Modifier
                        .onGloballyPositioned { ui.scoreTarget = it.boundsInRoot().center }
                        .graphicsLayer {
                            val t = ((ui.now - ui.scoreBumpAt) / 260f).coerceIn(0f, 1f)
                            // gentle dance while a combo is alive: every motion is a smooth wave (no cusps)
                            val beat = ui.now / 1000f * 2 * PI.toFloat() * 1.1f
                            val wave = 0.5f - 0.5f * cos(beat * 2)              // 0..1, smooth at both ends
                            val s = 1f + 0.2f * (1f - t) * (1f - t) + 0.035f * fever * wave
                            scaleX = s; scaleY = s
                            rotationZ = 3f * fever * sin(beat)
                            translationY = -4.dp.toPx() * fever * wave
                        },
                    style = TextStyle(textMotion = TextMotion.Animated, fontFeatureSettings = "tnum",
                        fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 54.sp, color = Pal.text,
                        shadow = Shadow(Pal.scoreShadow, Offset(0f, 7f), 0f),
                    ),
                )
            }
            ComboLine(ui)
            // less space above the board than below: the grid sits at the screen's center, nudged up
            Spacer(Modifier.weight(0.2f))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.45f)
                    .onSizeChanged { ui.metrics = Metrics(it.width.toFloat(), it.height.toFloat(), density) }
                    .onGloballyPositioned { ui.canvasOrigin = it.positionInRoot() }
                    .pointerInput(ui) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val m = ui.metrics ?: return@awaitEachGesture
                            val x = down.position.x
                            val y = down.position.y
                            if (ui.stuckAt >= 0) return@awaitEachGesture
                            val slot = m.hitSlot(x, y) ?: return@awaitEachGesture
                            if (ui.game.tray[slot] == null) return@awaitEachGesture
                            ui.startDrag(slot, x, y)
                            val d = ui.drag ?: return@awaitEachGesture
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                d.x = ch.position.x
                                d.y = ch.position.y
                                ch.consume()
                                if (!ch.pressed) break
                            }
                            ui.drag = null
                            ui.drop(d)
                        }
                    },
            ) { drawGame(ui, tm) }
            Spacer(Modifier.weight(1f))
        }
        if (!ui.noAds) BannerAd(ads)
      }
        FxLayer(ui, tm)
        if (ui.gameOverVisible) GameOver(ui)
      }
        if (ui.showSettings) Settings(ui)
    }
}

/**
 * A counting number that never wobbles sideways: every digit sits in a slot as wide as the widest digit,
 * so the text width only changes when a digit or comma is added (works with any font).
 */
@Composable
private fun FixedDigits(text: String, modifier: Modifier, style: TextStyle) {
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val digitW = remember(style) {
        with(density) { "0123456789".maxOf { tm.measure(it.toString(), style).size.width }.toDp() }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEach { ch ->
            if (ch.isDigit()) {
                Box(Modifier.width(digitW), contentAlignment = Alignment.Center) { BasicText(ch.toString(), style = style) }
            } else {
                BasicText(ch.toString(), style = style)
            }
        }
    }
}

private val rayPath = Path()

/**
 * Behind the score while a combo is alive: a slowly spinning golden sunburst with additive glow,
 * a pulsing halo, and glints orbiting the number. Grows with the combo ([k] 0..1).
 */
@Composable
private fun ComboAura(ui: GameUi, k: Float, modifier: Modifier) {
    Canvas(modifier) {
        val now = ui.now
        val c = center
        val gold = Color(0xFFFFC93C)
        val hot = Color(0xFFFF7A3D)
        val beat = now / 1000f * 2 * PI.toFloat() * 1.1f
        val pulse = 0.94f + 0.06f * (0.5f - 0.5f * cos(beat * 2))   // same smooth wave as the score
        val rOuter = 95.dp.toPx() * (0.85f + 0.25f * k) * pulse
        // halo
        drawCircle(Brush.radialGradient(listOf(gold, hot.copy(alpha = 0.5f), Color.Transparent), c, rOuter * 0.75f),
            rOuter * 0.75f, c, alpha = 0.45f * k, blendMode = BlendMode.Plus)
        // spinning rays
        val rays = 14
        val spin = now / 1000f * 22f
        rotate(spin, c) {
            for (i in 0 until rays) {
                val a0 = (i * 360f / rays) * PI.toFloat() / 180f
                val half = (360f / rays) * 0.22f * PI.toFloat() / 180f
                rayPath.reset()
                rayPath.moveTo(c.x, c.y)
                rayPath.lineTo(c.x + cos(a0 - half) * rOuter, c.y + sin(a0 - half) * rOuter)
                rayPath.lineTo(c.x + cos(a0 + half) * rOuter, c.y + sin(a0 + half) * rOuter)
                rayPath.close()
                drawPath(rayPath, Brush.radialGradient(listOf(gold, Color.Transparent), c, rOuter), alpha = 0.32f * k, blendMode = BlendMode.Plus)
            }
        }
        // orbiting glints
        val orbit = 70.dp.toPx() * (0.9f + 0.15f * k)
        for (i in 0 until 3) {
            val a = now / 1000f * 1.3f + i * 2.094f
            val tw = 0.75f + 0.25f * sin(now / 1000f * 3f + i * 2f)
            sparkle(c.x + cos(a) * orbit, c.y + sin(a) * orbit * 0.45f, 9.dp.toPx() * tw, k * tw, gold, rot = a * 30f)
        }
    }
}

/** Full-screen top layer: screen-edge flash and coins flying to the counter. Touches pass through. */
@Composable
private fun FxLayer(ui: GameUi, tm: TextMeasurer) {
    Canvas(Modifier.fillMaxSize()) {
        val now = ui.now
        // points: pop at the clear, then fly into the score counter
        for (f in ui.scoreFlights) {
            val age = (now - f.t0).toFloat()
            if (age < 0f) continue
            val start = Offset(ui.canvasOrigin.x + f.x, ui.canvasOrigin.y + f.y)
            val pos: Offset
            val sc: Float
            if (age < f.flyAt) {
                val k = (age / 180f).coerceAtMost(1f)
                sc = if (f.hold) 0.3f + 0.7f * easeOutBack(k) else 0.6f + 0.2f * k
                pos = Offset(start.x, start.y - (if (f.hold) 10.dp.toPx() * k else 0f))
            } else {
                val k = ((age - f.flyAt) / (f.total - f.flyAt)).coerceIn(0f, 1f)
                val e = k * k
                val from = Offset(start.x, start.y - (if (f.hold) 10.dp.toPx() else 0f))
                val bend = sin(k * PI.toFloat()) * 40.dp.toPx()
                pos = Offset(from.x + (ui.scoreTarget.x - from.x) * e - bend * 0.3f, from.y + (ui.scoreTarget.y - from.y) * e)
                sc = (if (f.hold) 1f else 0.8f) * (1f - 0.45f * e)
            }
            val size = if (f.hold) 72f else 44f
            // exactly the score counter's style (same font, white, soft drop shadow), no fat outline
            val fill = TextStyle(textMotion = TextMotion.Animated,
                fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = size.sp, color = Pal.text,
                shadow = Shadow(Pal.scoreShadow, Offset(0f, 7f), 0f),
            )
            val txt = "+${f.value}"
            val lf = tm.measure(txt, fill)
            scale(sc, pivot = pos) {
                drawText(lf, topLeft = Offset(pos.x - lf.size.width / 2f, pos.y - lf.size.height / 2f))
            }
        }
        val ft = (now - ui.flashAt) / 450f
        if (ft in 0f..1f) {
            drawRect(
                Brush.radialGradient(listOf(Color.Transparent, ui.flashColor), center, size.maxDimension * 0.75f),
                alpha = 0.45f * (1 - ft),
            )
        }
    }
}

// ---------------------------------------------------------------- blocks

private val quadPath = Path()

private fun DrawScope.quad(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, c: Color, a: Float) {
    quadPath.reset()
    quadPath.moveTo(x1, y1); quadPath.lineTo(x2, y2); quadPath.lineTo(x3, y3); quadPath.lineTo(x4, y4); quadPath.close()
    drawPath(quadPath, c, alpha = a)
}

private fun shade(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

/** Square bevelled "jewel" block: bright top edge, lit left, shaded right, dark bottom. */
fun DrawScope.block(x: Float, y: Float, s: Float, color: Color, alpha: Float = 1f) {
    val b = s * 0.14f
    val x2 = x + s
    val y2 = y + s
    drawRect(shade(color, 0.75f), Offset(x, y), Size(s, s), alpha = alpha)
    quad(x, y, x2, y, x2 - b, y + b, x + b, y + b, lerp(color, Color.White, 0.3f), alpha)
    quad(x, y, x + b, y + b, x + b, y2 - b, x, y2, lerp(color, Color.White, 0.08f), alpha)
    quad(x2, y, x2, y2, x2 - b, y2 - b, x2 - b, y + b, shade(color, 0.78f), alpha)
    quad(x, y2, x + b, y2 - b, x2 - b, y2 - b, x2, y2, shade(color, 0.6f), alpha)
    drawRect(
        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.04f), color), startY = y + b, endY = y2 - b),
        Offset(x + b, y + b), Size(s - 2 * b, s - 2 * b), alpha = alpha,
    )
}

/** Flat saturated-blue tile; the thin grid lines between tiles do the separating. */
fun DrawScope.emptyCell(x: Float, y: Float, s: Float) {
    drawRect(Pal.empty, Offset(x, y), Size(s, s))
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1
    return 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2)
}

private fun easeOut(t: Float) = 1 - (1 - t).pow(3)

private fun DrawScope.piece(p: Piece, tl: Offset, cell: Float, step: Float, color: Color, alpha: Float = 1f) {
    p.cells.forEach { block(tl.x + it.c * step, tl.y + it.r * step, cell, color, alpha) }
}

/** Soft drop shadow under a floating tray piece: each cell's shadow, offset down, in two faint layers (a cheap blur). */
private fun DrawScope.hoverShadow(p: Piece, tl: Offset, cell: Float, step: Float, dp: Float, alpha: Float) {
    val off = 5 * dp
    p.cells.forEach {
        val x = tl.x + it.c * step
        val y = tl.y + it.r * step + off
        drawRoundRect(Color.Black, Offset(x - 1.5f * dp, y - 1.5f * dp), Size(cell + 3 * dp, cell + 3 * dp), CornerRadius(4 * dp), alpha = 0.08f * alpha)
        drawRoundRect(Color.Black, Offset(x, y), Size(cell, cell), CornerRadius(2 * dp), alpha = 0.12f * alpha)
    }
}

private val starPath = Path()

/** Curved 4-point star (lens-glint shape): long thin rays pinched toward the center. */
private fun DrawScope.star(cx: Float, cy: Float, r: Float, c: Color, a: Float, blend: BlendMode = BlendMode.SrcOver) {
    val k = r * 0.11f
    starPath.reset()
    starPath.moveTo(cx, cy - r)
    starPath.quadraticTo(cx + k, cy - k, cx + r, cy)
    starPath.quadraticTo(cx + k, cy + k, cx, cy + r)
    starPath.quadraticTo(cx - k, cy + k, cx - r, cy)
    starPath.quadraticTo(cx - k, cy - k, cx, cy - r)
    starPath.close()
    drawPath(starPath, c, alpha = a, blendMode = blend)
}

/**
 * Glint: an ADDITIVE light bloom in the color (it brightens what's under it, like real neon),
 * a big tinted star, a small diagonal star, and a white-hot star core.
 */
private fun DrawScope.sparkle(x: Float, y: Float, r: Float, alpha: Float, color: Color = Color.White, rot: Float = 0f) {
    if (r <= 0f || alpha <= 0f) return
    val a = alpha.coerceIn(0f, 1f)
    val c = Offset(x, y)
    val bloom = r * 2.2f
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), c, bloom), bloom, c, alpha = 0.7f * a, blendMode = BlendMode.Plus)
    rotate(rot, c) {
        star(x, y, r, lerp(color, Color.White, 0.45f), a, BlendMode.Plus)
        rotate(45f, c) { star(x, y, r * 0.5f, lerp(color, Color.White, 0.6f), 0.8f * a, BlendMode.Plus) }
        star(x, y, r * 0.45f, Color.White, a)
    }
    drawCircle(Color.White, r * 0.13f, c, alpha = a)
}

/** Lines about to clear: glowing outline, a shine sweeping along, twinkling sparkles. */
private fun DrawScope.lineHighlight(m: Metrics, isRow: Boolean, idx: Int, color: Color, now: Long) {
    val e = 3 * m.dp
    val len = m.w - 2 * m.pad
    val tl = if (isRow) Offset(m.pad - e, m.cellY(idx) - e) else Offset(m.cellX(idx) - e, m.pad - e)
    val sz = if (isRow) Size(len + 2 * e, m.cell + 2 * e) else Size(m.cell + 2 * e, len + 2 * e)
    val pulse = 0.5f + 0.5f * sin(now / 90f)
    // neon in the piece's own color: soft layered halo + a bright tinted core line
    for (i in 1..4) {
        val g = i * 2.5f * m.dp
        drawRoundRect(color, tl - Offset(g, g), Size(sz.width + 2 * g, sz.height + 2 * g), CornerRadius(4 * m.dp + g),
            alpha = (0.32f / i) * (0.7f + 0.3f * pulse), style = Stroke(3 * m.dp))
    }
    drawRoundRect(lerp(color, Color.White, 0.55f), tl, sz, CornerRadius(4 * m.dp), alpha = 0.85f + 0.15f * pulse, style = Stroke(2.5f * m.dp))
    clipRect(tl.x, tl.y, tl.x + sz.width, tl.y + sz.height) {
        val ph = (now % 700) / 700f
        val band = m.cell * 1.6f
        if (isRow) {
            val x = tl.x - band + (sz.width + 2 * band) * ph
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White, Color.Transparent), x, x + band), Offset(x, tl.y), Size(band, sz.height), alpha = 0.6f)
        } else {
            val y = tl.y - band + (sz.height + 2 * band) * ph
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White, Color.Transparent), y, y + band), Offset(tl.x, y), Size(sz.width, band), alpha = 0.6f)
        }
    }
    for (k in 0 until 3) {
        val phase = now / 230f + k * 0.33f + idx * 0.17f
        val slot = ((floor(phase).toInt() * 5 + k * 3 + idx) % N + N) % N
        val tw = sin((phase - floor(phase)) * PI.toFloat())
        val cx = if (isRow) m.cellX(slot) + m.cell * 0.7f else m.cellX(idx) + m.cell * 0.3f
        val cy = if (isRow) m.cellY(idx) + m.cell * 0.3f else m.cellY(slot) + m.cell * 0.7f
        sparkle(cx, cy, m.cell * 0.3f * tw, tw, color)
    }
}

/** Neon halo around a block in its own color: layered soft strokes outside the box + a bright tinted rim. */
private fun DrawScope.neonGlow(x: Float, y: Float, s: Float, color: Color, k: Float, dp: Float) {
    if (k <= 0f) return
    // additive bloom: real "light" in the block's color, spilling past its edges
    val c = Offset(x + s / 2, y + s / 2)
    val br = s * 1.15f
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), c, br), br, c, alpha = 0.75f * k, blendMode = BlendMode.Plus)
    for (i in 1..5) {
        val g = i * 2.2f * dp
        drawRoundRect(color, Offset(x - g, y - g), Size(s + 2 * g, s + 2 * g), CornerRadius(2 * dp + g),
            alpha = (0.42f / i) * k, style = Stroke(2.6f * dp))
    }
    drawRect(lerp(color, Color.White, 0.6f), Offset(x, y), Size(s, s), alpha = 0.95f * k, style = Stroke(2 * dp))
}

/**
 * Star burst AROUND the block: glints shoot out past its edges, pop big, spin and twinkle out.
 * Staggered so they don't all flash at once.
 */
private fun DrawScope.neonSparkles(x: Float, y: Float, s: Float, color: Color, t: Float, seed: Int) {
    val r = Random(seed)
    val cx = x + s / 2
    val cy = y + s / 2
    val n = 4
    for (i in 0 until n) {
        val ang = (i * (360f / n) + r.nextFloat() * 60f) * PI.toFloat() / 180f
        val d = r.nextFloat() * 0.2f
        val k = ((t - d) / 0.8f).coerceIn(0f, 1f)
        if (k <= 0f) continue
        val dist = s * (0.55f + 0.85f * easeOut(k))
        val tw = sin(k * PI.toFloat())
        val size = s * (0.34f + r.nextFloat() * 0.16f) * (0.4f + 0.6f * tw)
        sparkle(cx + cos(ang) * dist, cy + sin(ang) * dist, size, tw, color, rot = k * 140f + r.nextFloat() * 45f)
    }
}

/** Narrow inner shadow: a short dark falloff just inside the top edge (and a hint on the left/right). */
private fun DrawScope.innerShadow(m: Metrics) {
    val l = m.gridLeft
    val sz = m.gridSize
    val shadow = Color.Black
    clipRect(l, l, l + sz, l + sz) {
        val top = m.cell * 0.15f
        val side = m.cell * 0.09f
        // 3-stop falloffs = soft, smooth edge (no hard band)
        drawRect(
            Brush.verticalGradient(listOf(shadow.copy(alpha = 0.42f), shadow.copy(alpha = 0.12f), Color.Transparent), l, l + top),
            Offset(l, l), Size(sz, top),
        )
        drawRect(
            Brush.horizontalGradient(listOf(shadow.copy(alpha = 0.26f), shadow.copy(alpha = 0.07f), Color.Transparent), l, l + side),
            Offset(l, l), Size(side, sz),
        )
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, shadow.copy(alpha = 0.05f), shadow.copy(alpha = 0.16f)), l + sz - side, l + sz),
            Offset(l + sz - side, l), Size(side, sz),
        )
    }
}

private val rimPath = Path()

private fun DrawScope.quadBrush(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, brush: Brush) {
    rimPath.reset()
    rimPath.moveTo(x1, y1); rimPath.lineTo(x2, y2); rimPath.lineTo(x3, y3); rimPath.lineTo(x4, y4); rimPath.close()
    drawPath(rimPath, brush)
}

/**
 * Thin inset bevel around the board so it looks pressed INTO the screen:
 * the top and left lips are in shade, the bottom and right lips catch the light.
 */
private fun DrawScope.insetRim(m: Metrics) {
    val l = m.gridLeft
    val sz = m.gridSize
    val r = l + sz
    val b = 2.5f * m.dp
    val o = l - b
    val e = r + b
    val bg = Pal.bg
    // each lip fades smoothly from the background (outer edge) to its shade/light (inner edge)
    quadBrush(o, o, e, o, r, l, l, l, Brush.verticalGradient(listOf(bg, shade(bg, 0.55f)), o, l))                      // top: deepest shade
    quadBrush(o, o, l, l, l, r, o, e, Brush.horizontalGradient(listOf(bg, shade(bg, 0.66f)), o, l))                    // left: shade
    quadBrush(e, o, e, e, r, r, r, l, Brush.horizontalGradient(listOf(lerp(bg, Color.White, 0.2f), bg), r, e))         // right: light
    quadBrush(o, e, l, r, r, r, e, e, Brush.verticalGradient(listOf(lerp(bg, Color.White, 0.34f), bg), r, e))          // bottom: brightest
}

// ---------------------------------------------------------------- the board

fun DrawScope.drawGame(ui: GameUi, tm: TextMeasurer) {
    val m = ui.metrics ?: return
    val st = (ui.now - ui.shakeAt) / 380f
    val dx = if (st in 0f..1f) ui.shakeAmp * (1 - st) * sin(st * 46f) else 0f
    val dy = if (st in 0f..1f) ui.shakeAmp * 0.6f * (1 - st) * cos(st * 39f) else 0f
    translate(dx, dy) { drawGameInner(ui, m, tm) }
}

private fun DrawScope.drawGameInner(ui: GameUi, m: Metrics, tm: TextMeasurer) {
    val now = ui.now
    val g = ui.game

    // thin 3D inset rim, then the grid lines (outer edge exactly one gap wide, same as between cells)
    insetRim(m)
    drawRect(Pal.grid, Offset(m.gridLeft, m.gridLeft), Size(m.gridSize, m.gridSize))

    // drop preview: ghost + lines that would clear
    val d = ui.drag
    var ghost: Set<Cell> = emptySet()
    var ghostColor = Color.White
    var hlRows: List<Int> = emptyList()
    var hlCols: List<Int> = emptyList()
    if (d != null) {
        val p = g.tray[d.slot]
        if (p != null) {
            val at = ui.target(d)
            if (at != null) {
                ghost = p.cells.map { Cell(at.r + it.r, at.c + it.c) }.toSet()
                ghostColor = Pal.block(p.color)
                val (rs, cs) = g.linesIfPlaced(p, at.r, at.c)
                hlRows = rs; hlCols = cs
            }
        }
    }

    // game over: board greys out row by row from the bottom
    val deadRows = if (ui.stuckAt >= 0) (((now - ui.stuckAt) / 650f) * (N + 1)).toInt() else 0

    // pass 1: empty tiles, then the board's inner shadow (so it sinks the tray but never dims the blocks)
    for (r in 0 until N) for (c in 0 until N) if (g[r, c] == 0) emptyCell(m.cellX(c), m.cellY(r), m.cell)
    innerShadow(m)

    // pass 2: blocks, ghost, highlights
    for (r in 0 until N) for (c in 0 until N) {
        val x = m.cellX(c)
        val y = m.cellY(r)
        val k = g[r, c]
        val hl = r in hlRows || c in hlCols
        if (k == 0) {
            if (Cell(r, c) in ghost) block(x, y, m.cell, ghostColor, alpha = if (hl) 1f else 0.4f)
        } else {
            val t = ((now - ui.bornAt[r * N + c]) / 200f).coerceIn(0f, 1f)
            val s = 0.84f + 0.16f * easeOutBack(t)
            val off = m.cell * (1 - s) / 2
            val col = when {
                N - 1 - r < deadRows -> Pal.dead
                hl -> ghostColor
                else -> Pal.block(k)
            }
            block(x + off, y + off, m.cell * s, col)
            if (t < 1f) drawRect(Color.White, Offset(x + off, y + off), Size(m.cell * s, m.cell * s), alpha = 0.45f * (1 - t))
        }
        if (hl) drawRect(lerp(ghostColor, Color.White, 0.35f), Offset(x, y), Size(m.cell, m.cell), alpha = 0.14f + 0.1f * sin(now / 70f))
    }
    hlRows.forEach { lineHighlight(m, true, it, ghostColor, now) }
    hlCols.forEach { lineHighlight(m, false, it, ghostColor, now) }

    // shockwave rings + glow at each clear
    ui.rings.forEach { rg ->
        val t = (now - rg.t0) / 600f
        if (t !in 0f..1f) return@forEach
        val col = lerp(Pal.block(rg.color), Color.White, 0.35f)
        val reach = m.cell * (if (rg.big) 6f else 4f)
        val gr = reach * (0.4f + 0.6f * easeOut(t))
        val ctr = Offset(rg.x, rg.y)
        drawCircle(Brush.radialGradient(listOf(Color.White, col, Color.Transparent), ctr, gr), gr, ctr, alpha = 0.55f * (1 - t))
        drawCircle(col, reach * easeOut(t), ctr, alpha = 0.9f * (1 - t), style = Stroke(m.cell * 0.32f * (1 - t) + 1f))
        drawCircle(Color.White, reach * 0.8f * easeOut(t), ctr, alpha = 0.7f * (1 - t), style = Stroke(m.cell * 0.12f * (1 - t) + 1f))
    }

    // line beams
    ui.beams.forEach { b ->
        val t = (now - b.t0) / 420f
        if (t !in 0f..1f) return@forEach
        val col = lerp(Pal.block(b.color), Color.White, 0.6f)
        val thick = m.cell * (0.6f + 1.2f * easeOut(t))
        val core = m.cell * 0.35f * (1 - t)
        if (b.isRow) {
            val cy = m.cellY(b.index) + m.cell / 2
            val len = m.w * easeOut((t * 2.2f).coerceAtMost(1f))
            drawRect(col, Offset((m.w - len) / 2, cy - thick / 2), Size(len, thick), alpha = 0.45f * (1 - t))
            drawRect(Color.White, Offset((m.w - len) / 2, cy - core / 2), Size(len, core), alpha = 0.9f * (1 - t))
        } else {
            val cx = m.cellX(b.index) + m.cell / 2
            val len = m.w * easeOut((t * 2.2f).coerceAtMost(1f))
            drawRect(col, Offset(cx - thick / 2, (m.w - len) / 2), Size(thick, len), alpha = 0.45f * (1 - t))
            drawRect(Color.White, Offset(cx - core / 2, (m.w - len) / 2), Size(core, len), alpha = 0.9f * (1 - t))
        }
    }

    // cleared blocks: 3D pop up with a NEON glow in the block's own color, sparkles twinkling AROUND it
    // (never a white wash inside), then it shrinks away into shards
    ui.clears.forEach { f ->
        val x = m.cellX(f.c)
        val y = m.cellY(f.r)
        val col = Pal.block(f.color)
        val t = (now - f.t0) / 380f
        if (t < 0f) { block(x, y, m.cell, col); return@forEach }
        if (t < 1f) {
            val s = if (t < 0.35f) 1f + 0.16f * easeOut(t / 0.35f) else 1.16f * (1 - easeOut((t - 0.35f) / 0.65f))
            val glow = if (t < 0.35f) easeOut(t / 0.35f) else 1f - (t - 0.35f) / 0.65f
            val off = m.cell * (1 - s) / 2
            val bx = x + off
            val by = y + off
            val bs = m.cell * s
            neonGlow(bx, by, bs, col, glow, m.dp)
            // lifted off the board: a small drop shadow sells the 3D pop
            drawRect(Color.Black, Offset(bx + bs * 0.06f, by + bs * 0.1f), Size(bs, bs), alpha = 0.25f * glow)
            block(bx, by, bs, col)
            neonSparkles(bx, by, bs, col, t, f.r * 7 + f.c * 13)
        }
        val pt = (now - f.t0 - 80) / 620f
        if (pt in 0f..1f) {
            val rnd = Random(f.r * 131 + f.c * 17 + f.t0.toInt())
            val cx = x + m.cell / 2
            val cy = y + m.cell / 2
            val sec = pt * 0.62f
            repeat(8) {
                val ang = rnd.nextFloat() * 2 * PI.toFloat()
                val sp = m.cell * (2.2f + rnd.nextFloat() * 3.5f)
                val px = cx + cos(ang) * sp * sec
                val py = cy + (sin(ang) * sp - m.cell * 3f) * sec + 0.5f * m.cell * 26f * sec * sec
                val sz = m.cell * (0.2f + rnd.nextFloat() * 0.18f) * (1 - pt * 0.5f)
                val rot = rnd.nextFloat() * 360f + pt * (rnd.nextFloat() - 0.5f) * 900f
                rotate(rot, Offset(px, py)) { block(px - sz / 2, py - sz / 2, sz, col, alpha = 1 - pt * pt) }
            }
            repeat(3) {
                val ang = rnd.nextFloat() * 2 * PI.toFloat()
                val sp = m.cell * (3f + rnd.nextFloat() * 3f)
                val px = cx + cos(ang) * sp * sec
                val py = cy + (sin(ang) * sp - m.cell * 4f) * sec + 0.5f * m.cell * 20f * sec * sec
                val c = Pal.confetti[rnd.nextInt(Pal.confetti.size)]
                val w = m.cell * 0.22f
                val rot = rnd.nextFloat() * 360f + pt * 1080f
                rotate(rot, Offset(px, py)) { drawRect(c, Offset(px - w / 2, py - w * 0.25f), Size(w, w * 0.5f), alpha = 1 - pt) }
            }
            repeat(3) {
                val sx = cx + (rnd.nextFloat() - 0.5f) * m.cell * 2.2f
                val sy = cy + (rnd.nextFloat() - 0.5f) * m.cell * 2.2f - pt * m.cell * 0.8f
                val tw = sin((pt * 1.4f + rnd.nextFloat()) * PI.toFloat()).coerceAtLeast(0f)
                sparkle(sx, sy, m.cell * 0.34f * tw, 1 - pt, col)
            }
        }
    }

    // tray (slides in when a new set is dealt)
    val r0 = ui.ret
    for (i in 0 until 3) {
        val p = g.tray[i] ?: continue
        if (d?.slot == i) continue
        val home = m.trayTopLeft(p, i)
        val tt = ((now - ui.trayAt - i * 70) / 300f).coerceIn(0f, 1f)
        val slide = (1 - easeOutBack(tt)) * m.w * 0.7f
        val fits = g.fitsAnywhere(p)
        val col = if (fits) Pal.block(p.color) else Pal.dead
        if (r0 != null && r0.slot == i) {
            val k = easeOut(((now - r0.t0) / 180f).coerceIn(0f, 1f))
            val tl = Offset(r0.from.x + (home.x - r0.from.x) * k, r0.from.y + (home.y - r0.from.y) * k)
            piece(p, tl, m.cell + (m.tCell(p) - m.cell) * k, m.step + (m.tStep(p) - m.step) * k, col)
        } else {
            // minimal hover: a soft shadow below, the piece floating gently above it
            val a = tt.coerceAtLeast(0.01f)
            val bob = sin(now / 520f + i * 2.1f) * 1.5f * m.dp
            val tl = Offset(home.x + slide, home.y - 3 * m.dp + bob)
            hoverShadow(p, Offset(home.x + slide, home.y), m.tCell(p), m.tStep(p), m.dp, a * (1f - bob / (6 * m.dp)))
            piece(p, tl, m.tCell(p), m.tStep(p), col, alpha = a)
        }
    }

    // dragged piece: grows from tray size to board size, floats above the finger
    if (d != null) {
        val p = g.tray[d.slot]
        if (p != null) {
            val k = easeOut(((now - d.t0) / 120f).coerceIn(0f, 1f))
            val from = m.trayTopLeft(p, d.slot)
            val e = ui.eff(d)
            val to = m.dragTopLeft(p, e.x, e.y)
            val tl = Offset(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k)
            piece(p, tl, m.tCell(p) + (m.cell - m.tCell(p)) * k, m.tStep(p) + (m.step - m.tStep(p)) * k, Pal.block(p.color))
        }
    }

    // floating texts with a dark outline
    ui.texts.forEach { f ->
        val t = (now - f.t0) / 1200f
        if (t < 0f || t > 1f) return@forEach
        // one layout; the black outline and the colored fill are passed EXPLICITLY at draw time
        // (the text measurer caches layouts, so colors baked into a measured style can't be trusted)
        val style = TextStyle(textMotion = TextMotion.Animated, fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = f.size.sp)
        val lay = tm.measure(f.text, style)
        val fillBrush: Brush? = when {
            f.rainbow -> {
                // gradient coords are relative to the word itself. A mirrored gradient repeats every 2·L,
                // so sliding exactly 2·L per loop flows forever with no jump.
                val L = lay.size.width * 0.55f
                val sh = (now % 1600) / 1600f * 2 * L
                Brush.linearGradient(Pal.confetti, start = Offset(-sh, 0f), end = Offset(-sh + L, L * 0.15f), tileMode = TileMode.Mirror)
            }
            f.text.startsWith("Combo") -> Brush.verticalGradient(listOf(Color(0xFFFFF6C2), Color(0xFFFFC93C), Color(0xFFFF9F1C)))
            else -> null
        }
        val rise = 46 * m.dp * easeOut(t)
        val alpha = if (t < 0.72f) 1f else (1 - t) / 0.28f
        val pop = if (t < 0.18f) 0.4f + 0.6f * easeOutBack(t / 0.18f) else 1f
        val half = lay.size.width / 2f
        val cx = if (m.w > 2 * half + 16 * m.dp) f.x.coerceIn(half + 8 * m.dp, m.w - half - 8 * m.dp) else m.w / 2
        val cy = f.y - rise
        val tl = Offset(cx - half, cy - lay.size.height / 2f)
        scale(pop, pivot = Offset(cx, cy)) {
            drawText(lay, color = Color.Black, topLeft = tl, alpha = alpha, drawStyle = Stroke(width = 7 * m.dp, join = StrokeJoin.Round))
            if (fillBrush != null) drawText(lay, brush = fillBrush, topLeft = tl, alpha = alpha, drawStyle = Fill)
            else drawText(lay, color = f.color, topLeft = tl, alpha = alpha, drawStyle = Fill)
        }
    }
}

// ---------------------------------------------------------------- chrome

private val styleBest get() = TextStyle(textMotion = TextMotion.Animated,
    fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Pal.text,
    shadow = Shadow(Pal.scoreShadow, Offset(0f, 4f), 0f),
)

@Composable
private fun RoundIconButton(icon: IconSpec, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Pal.pill)
            .juicy(onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Icon24(icon, Pal.text, 24.dp) }
}

/** Top bar: trophy + best score on the left, settings on the right. Nothing else. */
@Composable
private fun TopBar(ui: GameUi) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Trophy(30.dp)
            BasicText(fmt(ui.best), style = styleBest)
        }
        RoundIconButton(Icons.settings, "Settings") { ui.sfx(Sfx.TAP); ui.showSettings = true }
    }
}

@Composable
private fun ComboLine(ui: GameUi) {
    Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
        if (ui.combo >= 2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BasicText("Combo ×${ui.combo}", style = label(18, Pal.block(2), FontWeight.Bold).copy(shadow = Shadow(Color.Black, Offset(0f, 3f), 2f)))
            repeat(3) { i ->
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (i < ui.movesLeft) Pal.block(2) else Pal.pill))
            }
        }
    }
}

// ---------------- popups

@Composable
private fun Scrim(onDismiss: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color(0x73000000))
            .clickable(interactionSource = src, indication = null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Springs in from small: a popup, not a screen. */
@Composable
private fun PopIn(content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 360f)) }
    Box(
        Modifier.graphicsLayer {
            val s = 0.55f + 0.45f * a.value
            scaleX = s; scaleY = s
            alpha = a.value.coerceIn(0f, 1f)
            rotationZ = (1f - a.value) * -6f
        },
    ) { content() }
}

/** Chunky white card with a 3D bottom lip. Taps on it don't close the popup. */
@Composable
private fun ChunkyCard(content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(Modifier.width(320.dp).clickable(interactionSource = src, indication = null) {}) {
        Box(Modifier.matchParentSize().padding(top = 8.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xFFD9D3EE)))
        Column(
            Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.White)
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/**
 * Premium candy button (studio CTA conventions): soft drop shadow, dark outline, thick 3D lip,
 * gradient face with a glossy top band and inner rim light, a light sweep every few seconds,
 * outlined text with a drop shadow. Squashes on press (juicy).
 */
@Composable
private fun BigButton(text: String, color: Color, icon: IconSpec?, modifier: Modifier = Modifier, chip: String? = null, onClick: () -> Unit) {
    val shine by rememberInfiniteTransition(label = "shine").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "shine",
    )
    val dark = shade(color, 0.42f)
    // geometry (dp): outline 2.5, face 51, lip 8, drop shadow room 5  -> 69 total
    Box(modifier.fillMaxWidth().height(69.dp).juicy(onClick = onClick)) {
        Canvas(Modifier.matchParentSize()) {
            val d = density
            val w = size.width
            val edge = 2.5f * d
            val faceH = 51f * d
            val lip = 8f * d
            val h = edge * 2 + faceH + lip
            val r = 22f * d
            // soft drop shadow (two layers = cheap blur)
            drawRoundRect(Color.Black, Offset(1f * d, 5f * d), Size(w - 2f * d, h), CornerRadius(r), alpha = 0.10f)
            drawRoundRect(Color.Black, Offset(3f * d, 4f * d), Size(w - 6f * d, h), CornerRadius(r), alpha = 0.14f)
            // dark outline + 3D lip
            drawRoundRect(dark, Offset.Zero, Size(w, h), CornerRadius(r))
            drawRoundRect(
                Brush.verticalGradient(listOf(shade(color, 0.72f), shade(color, 0.58f)), startY = edge + faceH * 0.5f, endY = h - edge),
                Offset(edge, edge), Size(w - 2 * edge, h - 2 * edge), CornerRadius(r - edge),
            )
            // face
            val fx = edge
            val fy = edge
            val fw = w - 2 * edge
            val fr = r - edge
            drawRoundRect(
                Brush.verticalGradient(listOf(lerp(color, Color.White, 0.30f), color, shade(color, 0.93f)), startY = fy, endY = fy + faceH),
                Offset(fx, fy), Size(fw, faceH), CornerRadius(fr),
            )
            val facePath = Path().apply { addRoundRect(RoundRect(fx, fy, fx + fw, fy + faceH, CornerRadius(fr))) }
            clipPath(facePath) {
                // glossy top band
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.06f)), startY = fy + 3f * d, endY = fy + faceH * 0.48f),
                    Offset(fx + 9f * d, fy + 3f * d), Size(fw - 18f * d, faceH * 0.42f), CornerRadius(faceH * 0.21f),
                )
                // light sweep: crosses the face in the first 30% of each cycle, then rests
                val p = shine / 0.30f
                if (p < 1f) {
                    val cx = fx - 60f * d + p * (fw + 120f * d)
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.45f), 1f to Color.Transparent,
                            start = Offset(cx - 22f * d, fy), end = Offset(cx + 8f * d, fy + faceH),
                        ),
                        Offset(fx, fy), Size(fw, faceH),
                    )
                }
            }
            // inner rim light along the top of the face
            drawRoundRect(
                Color.White, Offset(fx + 1f * d, fy + 1f * d), Size(fw - 2f * d, faceH - 2f * d), CornerRadius(fr - 1f * d),
                alpha = 0.35f, style = Stroke(1.5f * d),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.5.dp).height(51.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { Icon24(icon, Color.White, 24.dp); Spacer(Modifier.width(10.dp)) }
            Box {
                val base = label(24, Color.White, FontWeight.Bold)
                BasicText(text, style = base.copy(color = dark, drawStyle = Stroke(width = 5f * LocalDensity.current.density, join = StrokeJoin.Round)))
                BasicText(text, style = base.copy(shadow = Shadow(dark, Offset(0f, 4f), 0f)))
            }
            if (chip != null) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(dark.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    BasicText(chip, style = label(13, Color.White, FontWeight.Bold))
                }
            }
        }
    }
}

/** Cute block buddy: blinks, bobs, smiles when happy, frowns (with a tear) when sad. */
@Composable
private fun Mascot(ui: GameUi, happy: Boolean, color: Color, sizeDp: Dp) {
    val ink = Color(0xFF2A1B3D)
    Canvas(Modifier.size(sizeDp)) {
        val t = ui.now / 1000f
        val s = size.width * 0.74f
        val bob = sin(t * 3.2f) * size.width * 0.035f
        val x = (size.width - s) / 2
        val y = (size.height - s) / 2 - size.width * 0.04f + bob
        drawOval(Color.Black, Offset(x + s * 0.12f, size.height * 0.88f), Size(s * 0.76f, s * 0.1f), alpha = 0.18f)
        block(x, y, s, color)
        val blink = (t % 3.2f) > 3.05f
        val ey = y + s * 0.42f
        for (ex in listOf(x + s * 0.34f, x + s * 0.66f)) {
            if (blink) drawLine(ink, Offset(ex - s * 0.07f, ey), Offset(ex + s * 0.07f, ey), s * 0.045f, StrokeCap.Round)
            else {
                drawCircle(Color.White, s * 0.1f, Offset(ex, ey))
                drawCircle(ink, s * 0.056f, Offset(ex, ey + (if (happy) -0.01f else 0.02f) * s))
                drawCircle(Color.White, s * 0.02f, Offset(ex + s * 0.022f, ey - s * 0.012f))
            }
        }
        drawCircle(Color(0xFFFF8FA3), s * 0.065f, Offset(x + s * 0.2f, y + s * 0.6f), alpha = 0.55f)
        drawCircle(Color(0xFFFF8FA3), s * 0.065f, Offset(x + s * 0.8f, y + s * 0.6f), alpha = 0.55f)
        val mouth = Path()
        if (happy) {
            mouth.moveTo(x + s * 0.38f, y + s * 0.62f)
            mouth.quadraticTo(x + s * 0.5f, y + s * 0.76f, x + s * 0.62f, y + s * 0.62f)
        } else {
            mouth.moveTo(x + s * 0.4f, y + s * 0.71f)
            mouth.quadraticTo(x + s * 0.5f, y + s * 0.6f, x + s * 0.6f, y + s * 0.71f)
            val tear = ((t * 0.9f) % 1f)
            drawCircle(Color(0xFF7FD3FF), s * 0.04f, Offset(x + s * 0.27f, ey + s * 0.1f + tear * s * 0.25f), alpha = 1f - tear)
        }
        drawPath(mouth, ink, style = Stroke(s * 0.05f, cap = StrokeCap.Round))
    }
}

/** Colored blocks gently raining behind a popup. */
@Composable
private fun RainBlocks(ui: GameUi) {
    Canvas(Modifier.fillMaxSize()) {
        val t = ui.now / 1000f
        val r = Random(42)
        repeat(16) {
            val bs = (18 + r.nextInt(22)).dp.toPx()
            val speed = (60 + r.nextInt(90)).dp.toPx()
            val x = r.nextFloat() * size.width
            val y = ((t * speed + r.nextFloat() * size.height) % (size.height + bs * 2)) - bs
            val rot = t * (r.nextFloat() - 0.5f) * 120f + r.nextFloat() * 360f
            val col = Pal.block(1 + r.nextInt(5))
            rotate(rot, Offset(x + bs / 2, y + bs / 2)) { block(x, y, bs, col, alpha = 0.55f) }
        }
    }
}

@Composable
private fun GameOver(ui: GameUi) {
    val g = ui.game
    Scrim {
        RainBlocks(ui)
        PopIn {
            ChunkyCard {
                Mascot(ui, happy = ui.beatBest, color = Pal.block(if (ui.beatBest) 2 else 4), sizeDp = 100.dp)
                BasicText(if (ui.beatBest) "New Best!" else "Out of space!", style = label(30, Pal.ink, FontWeight.Bold))
                BasicText(fmt(g.score), style = label(58, Pal.ink, FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Trophy(22.dp)
                    BasicText(fmt(ui.best), style = label(20, Pal.inkMuted, FontWeight.SemiBold))
                }
                Spacer(Modifier.height(2.dp))
                if (!g.usedBlast) BigButton("Revive", Color(0xFF3FBF6E), Icons.play, chip = "AD") { ui.revive() }
                BigButton("Play again", Color(0xFF3D8BFF), Icons.replay) { ui.replay() }
            }
        }
    }
}

// ---------------- settings

private val noAdsIcon = IconSpec(listOf("M5.6 5.6l12.8 12.8"), listOf(Triple(12f, 12f, 9f)))

/**
 * Candy body shared by the settings controls (same family as BigButton): soft drop shadow, dark outline,
 * 3D lip, gradient face, glossy top band, rim light. The face occupies [edge, edge + faceH] from the top.
 */
private fun DrawScope.candy(color: Color, edgeDp: Float, faceHDp: Float, lipDp: Float, rDp: Float, glossy: Boolean = true) {
    val d = density
    val w = size.width
    val edge = edgeDp * d
    val faceH = faceHDp * d
    val h = edge * 2 + faceH + lipDp * d
    val r = rDp * d
    val dark = shade(color, 0.42f)
    drawRoundRect(Color.Black, Offset(2f * d, 4f * d), Size(w - 4f * d, h), CornerRadius(r), alpha = 0.16f)
    drawRoundRect(dark, Offset.Zero, Size(w, h), CornerRadius(r))
    drawRoundRect(
        Brush.verticalGradient(listOf(shade(color, 0.74f), shade(color, 0.6f)), startY = edge + faceH * 0.5f, endY = h - edge),
        Offset(edge, edge), Size(w - 2 * edge, h - 2 * edge), CornerRadius(r - edge),
    )
    val fw = w - 2 * edge
    val fr = r - edge
    drawRoundRect(
        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.30f), color, shade(color, 0.93f)), startY = edge, endY = edge + faceH),
        Offset(edge, edge), Size(fw, faceH), CornerRadius(fr),
    )
    if (glossy) drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.05f)), startY = edge + 2f * d, endY = edge + faceH * 0.5f),
        Offset(edge + 6f * d, edge + 2.5f * d), Size(fw - 12f * d, faceH * 0.42f), CornerRadius(minOf(faceH * 0.21f, fr)),
    )
    drawRoundRect(
        Color.White, Offset(edge + 1f * d, edge + 1f * d), Size(fw - 2f * d, faceH - 2f * d), CornerRadius(fr - 1f * d),
        alpha = 0.32f, style = Stroke(1.4f * d),
    )
}

/** Text with a dark outline + drop shadow, like the big buttons. */
@Composable
private fun CandyText(text: String, size: Int, dark: Color, stroke: Float = 4f) {
    val base = label(size, Color.White, FontWeight.Bold)
    Box {
        BasicText(text, style = base.copy(color = dark, drawStyle = Stroke(width = stroke * LocalDensity.current.density, join = StrokeJoin.Round)))
        BasicText(text, style = base.copy(shadow = Shadow(dark, Offset(0f, 3f), 0f)))
    }
}

/**
 * Settings row: a soft raised panel with the label, and a premium switch: a sunken track (inner shadow)
 * with a glossy bevelled knob that springs across; the track fills candy green when on.
 */
@Composable
private fun ToggleRow(text: String, on: Boolean, onClick: () -> Unit) {
    val knob by animateDpAsState(if (on) 28.dp else 3.dp, spring(dampingRatio = 0.6f, stiffness = 700f), label = "knob")
    val track by animateColorAsState(if (on) Color(0xFF3FBF6E) else Color(0xFFB9B1D6), tween(180), label = "track")
    Box(Modifier.fillMaxWidth().height(60.dp).juicy(onClick = onClick)) {
        Canvas(Modifier.matchParentSize()) {
            val d = density
            val w = size.width
            // panel: lilac lip under a pale face
            drawRoundRect(Color(0xFFD5CCF2), Offset(0f, 4f * d), Size(w, 54f * d), CornerRadius(17f * d))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White, Color(0xFFF1ECFC)), startY = 0f, endY = 54f * d),
                Offset.Zero, Size(w, 54f * d), CornerRadius(17f * d),
            )
            // sunken track
            val tw = 60f * d
            val th = 32f * d
            val tx = w - 16f * d - tw
            val ty = (54f * d - th) / 2
            drawRoundRect(shade(track, 0.55f), Offset(tx, ty), Size(tw, th), CornerRadius(th / 2))
            drawRoundRect(track, Offset(tx, ty + 2.5f * d), Size(tw, th - 2.5f * d), CornerRadius(th / 2))
            drawRoundRect(Color.Black, Offset(tx, ty), Size(tw, th), CornerRadius(th / 2), alpha = 0.18f, style = Stroke(1.2f * d))
            // knob: shadow, bevelled white body, gloss
            val ks = 26f * d
            val kx = tx + knob.toPx()
            val ky = ty + (th - ks) / 2
            drawCircle(Color.Black, ks / 2, Offset(kx + ks / 2, ky + ks / 2 + 2.5f * d), alpha = 0.22f)
            drawCircle(Color(0xFFD9D2EE), ks / 2, Offset(kx + ks / 2, ky + ks / 2 + 1.2f * d))
            drawCircle(
                Brush.verticalGradient(listOf(Color.White, Color(0xFFF0ECFA)), startY = ky, endY = ky + ks),
                ks / 2 - 1f * d, Offset(kx + ks / 2, ky + ks / 2),
            )
        }
        BasicText(
            text, style = label(21, Pal.ink, FontWeight.Bold),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun TileButton(icon: IconSpec, text: String, color: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = if (enabled) color else Color(0xFFB9B3CF)
    // outline 2.5, face 74, lip 7, shadow room 4 -> 90
    Box(modifier.height(90.dp).juicy(enabled, onClick)) {
        Canvas(Modifier.matchParentSize()) { candy(c, 2.5f, 74f, 7f, 20f) }
        Column(
            Modifier.fillMaxWidth().padding(top = 2.5.dp).height(74.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon24(icon, Color.White, 30.dp)
            Spacer(Modifier.height(3.dp))
            CandyText(text, 16, shade(c, 0.42f), stroke = 3.5f)
        }
    }
}

/** Round candy close button (red), top-right of popups. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(Modifier.size(width = 44.dp, height = 50.dp).juicy(onClick = onClick).semantics { contentDescription = "Close" }) {
        Canvas(Modifier.matchParentSize()) { candy(Color(0xFFFF4D5E), 2f, 38f, 5f, 21f) }
        Box(Modifier.padding(top = 2.dp).size(width = 44.dp, height = 38.dp), contentAlignment = Alignment.Center) {
            Icon24(Icons.close, Color.White, 20.dp)
        }
    }
}

@Composable
private fun Settings(ui: GameUi) {
    Scrim(onDismiss = { ui.showSettings = false }) {
        PopIn {
            ChunkyCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText("Settings", style = label(28, Pal.ink, FontWeight.Bold))
                    Box(Modifier.align(Alignment.CenterEnd)) { CloseButton { ui.sfx(Sfx.TAP); ui.showSettings = false } }
                }
                ToggleRow("Sound", !ui.muted) { ui.toggleSound() }
                ToggleRow("Music", ui.music) { ui.toggleMusic() }
                ToggleRow("Vibrate", ui.vibrate) { ui.toggleVibrate() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TileButton(Icons.home, "Home", Color(0xFF8A6CFF), Modifier.weight(1f)) { ui.goHome() }
                    TileButton(Icons.replay, "Replay", Color(0xFF3D8BFF), Modifier.weight(1f)) { ui.replay() }
                    // test build: tapping again turns ads back on; release: a real purchase, and "Ads off" stays
                    TileButton(noAdsIcon, if (ui.noAds) "Ads off" else "No Ads", Color(0xFFFF8A3D), Modifier.weight(1f), enabled = !ui.noAds || BuildConfig.DEBUG) { ui.toggleNoAds() }
                }
            }
        }
    }
}

// ---------------- home

/** "BLAST" spelled on bobbing jewel blocks. */
@Composable
private fun LogoBlocks(ui: GameUi) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        "BLAST".forEachIndexed { i, ch ->
            Box(
                Modifier.size(52.dp).graphicsLayer { translationY = sin(ui.now / 260f + i * 0.9f) * 6.dp.toPx() },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) { block(0f, 0f, size.width, Pal.block(i + 1)) }
                BasicText(ch.toString(), style = label(30, Color.White, FontWeight.Bold).copy(shadow = Shadow(Color(0x66000000), Offset(0f, 3f), 0f)))
            }
        }
    }
}

/**
 * Home background ambience, done the way studio menus do it:
 * - real 3D jewel blocks, kept SOLID and pushed back with atmospheric haze (mixed toward the theme color), never see-through
 * - depth: far layer small/faint/slow, near layer bigger/brighter/faster (parallax)
 * - calm motion: slow rise, gentle sway and tilt, fade in/out at top and bottom
 * - framing: they live at the sides and fade near the center column, so the logo, mascot and buttons stay clean
 * - a few rising light motes that twinkle
 */
@Composable
private fun DriftBlocks(ui: GameUi) {
    Canvas(Modifier.fillMaxSize()) {
        val t = ui.now / 1000f
        val w = size.width
        val h = size.height
        val r = Random(11)
        // layer: count, size dp, speed dp/s -> presence (how much of the block's own color shows through the haze)
        val layers = listOf(Triple(6, 16f, 9f) to 0.50f, Triple(5, 26f, 16f) to 0.66f, Triple(3, 38f, 26f) to 0.82f)
        layers.forEach { (spec, a0) ->
            val (count, sizeDp, speedDp) = spec
            repeat(count) { i ->
                val tint = Pal.block(1 + r.nextInt(5))
                val bs = (sizeDp * (0.85f + 0.3f * r.nextFloat())).dp.toPx()
                val speed = speedDp.dp.toPx() * (0.85f + 0.3f * r.nextFloat())
                val phase = r.nextFloat()
                // alternate sides, stay mostly in the outer bands
                val side = if ((i + count) % 2 == 0) 0f else 1f
                val band = 0.03f + 0.17f * r.nextFloat()
                val baseX = if (side == 0f) band * w else (1f - band) * w
                val span = h + bs * 2
                val y = h + bs - ((t * speed + phase * span) % span)
                val x = baseX + sin(t * 0.5f + phase * 6.28f) * 10.dp.toPx()
                val tilt = (r.nextFloat() - 0.5f) * 24f + sin(t * 0.4f + phase * 9f) * 6f
                // fade at the top/bottom and toward the center column
                val vy = (y / h).coerceIn(0f, 1f)
                // fade out at the bottom and well before the top bar (trophy / gear)
                val edgeFade = minOf(((vy - 0.14f) / 0.12f), ((1f - vy) / 0.10f)).coerceIn(0f, 1f)
                val cx = kotlin.math.abs(x - w / 2) / (w / 2)
                val centerFade = ((cx - 0.55f) / 0.25f).coerceIn(0f, 1f)
                val fade = edgeFade * centerFade
                val a = a0 * fade
                if (a > 0.01f) rotate(tilt, Offset(x, y)) { hazeBlock(x - bs / 2, y - bs / 2, bs, tint, a) }
            }
        }
        // rising light motes
        repeat(12) { i ->
            val speed = (10f + 14f * r.nextFloat()).dp.toPx()
            val phase = r.nextFloat()
            val span = h + 40.dp.toPx()
            val y = h + 20.dp.toPx() - ((t * speed + phase * span) % span)
            val x = r.nextFloat() * w + sin(t * 0.7f + i) * 14.dp.toPx()
            val vy = (y / h).coerceIn(0f, 1f)
            val fade = (minOf(vy, 1f - vy) / 0.15f).coerceIn(0f, 1f)
            val tw = 0.55f + 0.45f * sin(t * (1.5f + phase) + i * 2f)
            val rad = (1.4f + 1.8f * phase).dp.toPx()
            drawCircle(Color.White, rad * 3f, Offset(x, y), alpha = 0.05f * fade * tw)
            drawCircle(Color.White, rad, Offset(x, y), alpha = 0.32f * fade * tw)
        }
    }
}

/**
 * The game's 3D jewel block, pushed back into the distance with atmospheric haze: the block stays SOLID
 * (no see-through mud) and its colors are mixed toward the theme background. presence 0 = invisible, 1 = full color.
 */
private fun DrawScope.hazeBlock(x: Float, y: Float, s: Float, color: Color, presence: Float) {
    val bg = Pal.bg
    val b = s * 0.14f
    val x2 = x + s
    val y2 = y + s
    fun hz(c: Color) = lerp(bg, c, presence)
    drawRect(hz(shade(color, 0.75f)), Offset(x, y), Size(s, s))
    quad(x, y, x2, y, x2 - b, y + b, x + b, y + b, hz(lerp(color, Color.White, 0.3f)), 1f)
    quad(x, y, x + b, y + b, x + b, y2 - b, x, y2, hz(lerp(color, Color.White, 0.08f)), 1f)
    quad(x2, y, x2, y2, x2 - b, y2 - b, x2 - b, y + b, hz(shade(color, 0.78f)), 1f)
    quad(x, y2, x + b, y2 - b, x2 - b, y2 - b, x2, y2, hz(shade(color, 0.6f)), 1f)
    drawRect(hz(color), Offset(x + b, y + b), Size(s - 2 * b, s - 2 * b))
}

/** The mascot art (design/mascot_source.jpg, cut out): floats, sways gently, with glints twinkling around it. */
@Composable
private fun HomeMascot(ui: GameUi) {
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val t = ui.now / 1000f
            val glints = listOf(Offset(0.12f, 0.22f), Offset(0.9f, 0.18f), Offset(0.92f, 0.72f), Offset(0.08f, 0.7f), Offset(0.5f, 0.02f))
            glints.forEachIndexed { i, p ->
                val tw = sin(t * 2.2f + i * 1.7f).coerceAtLeast(0f)
                sparkle(p.x * size.width, p.y * size.height, 11.dp.toPx() * tw, tw, Color(0xFFFFE07A), rot = t * 40f + i * 30f)
            }
            // soft ground shadow that breathes with the float
            val lift = 0.5f - 0.5f * cos(t * 2.4f)
            drawOval(Color.Black, Offset(size.width * (0.3f + 0.03f * lift), size.height * 0.85f),
                Size(size.width * (0.4f - 0.06f * lift), size.height * 0.05f), alpha = 0.18f - 0.05f * lift)
        }
        // mascot over its soft white glow (tools/soft_glow.py); both float and sway together,
        // and the glow swells a little at the top of each float
        Box(
            Modifier.size(170.dp).graphicsLayer {
                val t = ui.now / 1000f
                val lift = 0.5f - 0.5f * cos(t * 2.4f)
                translationY = -10.dp.toPx() * lift
                rotationZ = 3f * sin(t * 1.2f)
            },
        ) {
            Image(
                painterResource(R.drawable.mascot_glow),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = 0.65f + 0.35f * (0.5f - 0.5f * cos(ui.now / 1000f * 2.4f))
                },
            )
            Image(
                painterResource(R.drawable.mascot),
                contentDescription = "Gemblo, the block buddy",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HomeScreen(ui: GameUi) {
    DriftBlocks(ui)
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(ui)
        Spacer(Modifier.weight(0.8f))
        // the logo art (design/logo_source.jpg, cut out) over a soft white glow (tools/soft_glow.py),
        // breathing gently; the glow swells with the breath
        Box(
            Modifier.fillMaxWidth(0.92f).aspectRatio(1442f / 746f).graphicsLayer {
                val s = 1f + 0.025f * (0.5f - 0.5f * cos(ui.now / 1000f * 1.8f))
                scaleX = s; scaleY = s
            },
        ) {
            Image(
                painterResource(R.drawable.logo_glow),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = 0.7f + 0.3f * (0.5f - 0.5f * cos(ui.now / 1000f * 1.8f))
                },
            )
            Image(
                painterResource(R.drawable.logo),
                contentDescription = "Gemblo Blast",
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(16.dp))
        HomeMascot(ui)
        Spacer(Modifier.weight(1f))
        if (ui.canContinue) {
            // pick up exactly where the player left off
            BigButton("Continue", Color(0xFF3FBF6E), Icons.play, Modifier.width(260.dp), chip = fmt(ui.game.score)) { ui.play() }
            Spacer(Modifier.height(14.dp))
            BasicText(
                "New game",
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).juicy { ui.replay() }.padding(horizontal = 18.dp, vertical = 10.dp),
                style = label(20, Pal.text, FontWeight.Bold).copy(shadow = Shadow(Pal.scoreShadow, Offset(0f, 3f), 0f)),
            )
        } else {
            BigButton("Play", Color(0xFF3FBF6E), Icons.play, Modifier.width(260.dp)) { ui.play() }
        }
        Spacer(Modifier.weight(0.7f))
    }
}
