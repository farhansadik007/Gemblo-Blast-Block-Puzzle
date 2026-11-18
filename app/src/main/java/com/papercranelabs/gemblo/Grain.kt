package com.papercranelabs.gemblo

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.random.Random

/** One seamless tile of film grain: tiny light and dark specks at very low alpha. Built once. */
private val grainTile by lazy {
    val s = 192
    val rnd = Random(7)
    val px = IntArray(s * s) {
        val v = rnd.nextFloat()
        when {
            v < 0.30f -> (rnd.nextInt(6, 15) shl 24) or 0xFFFFFF   // light speck
            v < 0.62f -> (rnd.nextInt(8, 20) shl 24)               // dark speck
            else -> 0
        }
    }
    Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Subtle static film grain over whatever is drawn behind (the solid theme color). */
fun Modifier.grain(): Modifier = drawWithCache {
    val k = (density / 1.6f).coerceAtLeast(1f)          // speck size ~0.6dp, fine on every screen
    val brush = ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind {
        scale(k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawRect(brush, size = size.copy(width = size.width / k, height = size.height / k))
        }
    }
}
