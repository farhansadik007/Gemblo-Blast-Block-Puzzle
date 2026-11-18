package com.papercranelabs.gemblo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stroke icons on a 24x24 grid (same paths as the mockup). */
class IconSpec(val paths: List<String>, val circles: List<Triple<Float, Float, Float>> = emptyList())

object Icons {
    /** Gear (outline from the ISC-licensed Lucide icon set). */
    val settings = IconSpec(
        listOf(
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38" +
                "a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38" +
                "a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18" +
                "a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08" +
                "a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08" +
                "a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        ),
        listOf(Triple(12f, 12f, 3f)),
    )
    val hammer = IconSpec(listOf("M13 3l8 8-3 3-8-8z", "M12 10l-8 8a1.5 1.5 0 0 0 2 2l8-8"))
    val rotate = IconSpec(listOf("M20 12a8 8 0 1 1-2.3-5.6", "M20 4v5h-5"))
    val shuffle = IconSpec(listOf("M4 7h4l8 10h4", "M4 17h4l2.5-3", "M13.5 10L16 7h4", "M18 4l3 3-3 3", "M18 14l3 3-3 3"))
    val bomb = IconSpec(listOf("M15 8l3-3", "M19.5 2.5l1 1"), listOf(Triple(11f, 14f, 7f)))
    val home = IconSpec(listOf("M3 11l9-8 9 8", "M5 10v10h5v-6h4v6h5V10"))
    val replay = IconSpec(listOf("M4 12a8 8 0 1 0 2.3-5.6", "M4 4v5h5"))
    val close = IconSpec(listOf("M6 6l12 12", "M18 6L6 18"))
    val playSolid = IconSpec(listOf("M8 5v14l11-7z"))
    val play = IconSpec(listOf("M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M10 9.5v5l4.5-2.5z"))
}

@Composable
fun Icon24(spec: IconSpec, color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val paths = remember(spec) { spec.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        scale(s, pivot = Offset.Zero) {
            val st = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            paths.forEach { drawPath(it, color, style = st) }
            spec.circles.forEach { (x, y, r) -> drawCircle(color, r, Offset(x, y), style = st) }
        }
    }
}

private val crownPath = "M3 8l4.5 4L12 5l4.5 7L21 8l-2 11H5z"

@Composable
fun Crown(size: Dp) {
    val path = remember { PathParser().parsePathString(crownPath).toPath() }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, Color(0xFFFFC93C), style = Fill)
            drawPath(path, Color(0xFFE09A12), style = Stroke(1.5f, join = StrokeJoin.Round))
        }
    }
}

private val trophyCup = "M7 3h10v5a5 5 0 0 1-10 0z"
private val trophyHandles = "M7 5H4a3 3 0 0 0 3 4.5M17 5h3a3 3 0 0 1-3 4.5"
private val trophyBase = "M10 13h4v3h-4zM7.5 16h9v4h-9z"

/** Gold trophy for the best score. */
@Composable
fun Trophy(size: Dp) {
    val cup = remember { PathParser().parsePathString(trophyCup).toPath() }
    val handles = remember { PathParser().parsePathString(trophyHandles).toPath() }
    val base = remember { PathParser().parsePathString(trophyBase).toPath() }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(handles, Color(0xFFFFC93C), style = Stroke(2f, cap = StrokeCap.Round))
            drawPath(cup, Color(0xFFFFC93C), style = Fill)
            drawPath(base, Color(0xFFFFC93C), style = Fill)
            drawPath(cup, Color(0xFFE09A12), style = Stroke(1.2f, join = StrokeJoin.Round))
            drawPath(base, Color(0xFFE09A12), style = Stroke(1.2f, join = StrokeJoin.Round))
            drawRect(Color.White, Offset(9f, 4.2f), androidx.compose.ui.geometry.Size(1.4f, 4f), alpha = 0.6f)
        }
    }
}

@Composable
fun Coin(size: Dp, modifier: Modifier = Modifier, face: Color = Color(0xFFFFC93C), rim: Color = Color(0xFFE09A12)) {
    Canvas(modifier.size(size)) {
        val r = this.size.width / 2f
        drawCircle(face, r * 0.85f)
        drawCircle(rim, r * 0.85f, style = Stroke(r * 0.15f))
        drawCircle(rim, r * 0.42f, style = Stroke(r * 0.15f))
    }
}
