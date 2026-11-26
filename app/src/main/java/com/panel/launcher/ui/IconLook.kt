package com.panel.launcher.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** How every app icon is drawn: one shared shape, plus an optional pastel colour filter. */
data class IconLook(val shape: Shape, val filter: ColorFilter?)

val LocalIconLook = staticCompositionLocalOf { IconLook(RoundedCornerShape(50), null) }

/** iOS-style superellipse ("squircle"). */
val SquircleShape: Shape = GenericShape { size, _ ->
    val a = size.width / 2f
    val b = size.height / 2f
    val exp = 2.0 / 4.5 // n = 4.5: smooth corners, flat sides
    val steps = 96
    for (i in 0..steps) {
        val t = 2 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = a + a * (sign(c) * abs(c).pow(exp)).toFloat()
        val y = b + b * (sign(s) * abs(s).pow(exp)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

fun iconShapeFor(kind: String, roundness: Int): Shape = when (kind) {
    "squircle" -> SquircleShape
    "circle" -> CircleShape
    else -> RoundedCornerShape(roundness.coerceIn(0, 50))
}

/**
 * Pastel filter: [strength] 0…1 lowers saturation and lifts colours gently toward white,
 * applied while drawing so the slider previews instantly.
 */
fun pastelFilter(strength: Float): ColorFilter? {
    if (strength <= 0.01f) return null
    val sat = 1f - 0.6f * strength
    val lift = 0.3f * strength
    val keep = 1f - lift
    val rw = 0.2126f; val gw = 0.7152f; val bw = 0.0722f
    val inv = 1f - sat
    fun row(r: Float, g: Float, b: Float) = floatArrayOf(r * keep, g * keep, b * keep, 0f, 255f * lift)
    val values = row(rw * inv + sat, gw * inv, bw * inv) +
        row(rw * inv, gw * inv + sat, bw * inv) +
        row(rw * inv, gw * inv, bw * inv + sat) +
        floatArrayOf(0f, 0f, 0f, 1f, 0f)
    return ColorFilter.colorMatrix(ColorMatrix(values))
}
