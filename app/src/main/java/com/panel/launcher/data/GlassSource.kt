package com.panel.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * Makes the small, pre-blurred copy of the wallpaper that glass surfaces draw from. It's tiny
 * (96 px wide) so it costs almost nothing to keep, and scaling it up gives a smooth frosted look.
 */
object GlassSource {
    fun from(src: Bitmap): Bitmap {
        val w = 96
        val h = (src.height * w / src.width.toFloat()).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            // A little extra colour, like iOS's "vibrant" glass.
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.35f) })
        }
        Canvas(small).drawBitmap(src, null, Rect(0, 0, w, h), paint)
        repeat(3) { boxBlur(small, 2) }
        return small
    }

    /** In-place box blur (horizontal then vertical), edges clamped. */
    private fun boxBlur(bmp: Bitmap, r: Int) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val len = if (horizontal) w else h
            for (line in 0 until lines) {
                for (i in 0 until len) {
                    var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val c = if (horizontal) src[line * w + j] else src[j * w + line]
                        a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                    }
                    val idx = if (horizontal) line * w + i else i * w + line
                    dst[idx] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
        }
        pass(px, out, true)
        pass(out, px, false)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }
}
