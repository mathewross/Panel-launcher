package com.panel.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** Small pre-blurred copy of the wallpaper (null when Panel can't see the wallpaper). */
val LocalGlass = staticCompositionLocalOf<ImageBitmap?> { null }

/**
 * Frosted glass: draws the blurred wallpaper that's directly behind this surface, then a tint,
 * a soft top highlight and a hairline edge. Without a wallpaper to blur it falls back to a
 * translucent gradient that still reads as glass.
 */
fun Modifier.glass(shape: Shape, palette: TilePalette): Modifier = composed {
    val src = LocalGlass.current
    val view = LocalView.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    this
        .onGloballyPositioned { coords[0] = it }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val highlight = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (palette.light) 0.25f else 0.10f),
                0.5f to Color.Transparent,
            )
            val fallback = Brush.verticalGradient(listOf(palette.bg, palette.bg.copy(alpha = palette.bg.alpha * 0.7f)))
            onDrawBehind {
                clipPath(path) {
                    val c = coords[0]
                    if (src != null && c != null && c.isAttached) {
                        // Map this surface's window position onto the centre-cropped wallpaper.
                        val pos = c.positionInWindow()
                        val sw = view.rootView.width.toFloat().coerceAtLeast(1f)
                        val sh = view.rootView.height.toFloat().coerceAtLeast(1f)
                        val s = max(sw / src.width, sh / src.height)
                        val ox = (sw - src.width * s) / 2f
                        val oy = (sh - src.height * s) / 2f
                        val srcW = (size.width / s).roundToInt().coerceIn(1, src.width)
                        val srcH = (size.height / s).roundToInt().coerceIn(1, src.height)
                        val srcX = ((pos.x - ox) / s).roundToInt().coerceIn(0, src.width - srcW)
                        val srcY = ((pos.y - oy) / s).roundToInt().coerceIn(0, src.height - srcH)
                        drawImage(
                            src, IntOffset(srcX, srcY), IntSize(srcW, srcH),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                        drawRect(palette.glassTint)
                    } else {
                        drawRect(fallback)
                    }
                    drawRect(highlight)
                }
                drawOutline(outline, palette.border, style = Stroke(1.dp.toPx()))
            }
        }
}

/**
 * Full-screen frosted backdrop used behind the app panel, folders and menus. [alpha] lets it
 * fade in with the panel.
 */
@Composable
fun BlurredBackdrop(alpha: () -> Float, tint: Color = Color(0x66000000), modifier: Modifier = Modifier) {
    val src = LocalGlass.current
    Box(modifier.fillMaxSize().graphicsLayer { this.alpha = alpha() }) {
        if (src != null) {
            Image(src, null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(tint))
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
    }
}

@Suppress("unused")
private val unusedOffset = Offset.Zero
