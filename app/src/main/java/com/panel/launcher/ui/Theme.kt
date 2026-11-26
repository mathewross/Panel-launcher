package com.panel.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import com.panel.launcher.data.TileStyle

val Accent = Color(0xFFFF453A)       // iOS-style red used for dates / "Up next"
val SheetColor = Color(0xFF1C1C1E)
val SheetRaised = Color(0xFF2C2C2E)

data class TilePalette(val bg: Color, val fg: Color, val subtle: Color, val border: Color, val glass: Boolean = false)

fun paletteFor(style: TileStyle) = when (style) {
    TileStyle.GLASS -> TilePalette(
        bg = Color(0x3DFFFFFF),
        fg = Color.White, subtle = Color.White.copy(alpha = 0.82f), border = Color.White.copy(alpha = 0.28f),
        glass = true,
    )
    TileStyle.LIGHT -> TilePalette(Color(0xF5FFFFFF), Color(0xFF111111), Color(0xFF6E6E73), Color.Transparent)
    TileStyle.DARK -> TilePalette(Color(0xEB1C1C1E), Color.White, Color(0xFF9A9AA0), Color.Transparent)
}

val LocalTiles = staticCompositionLocalOf { paletteFor(TileStyle.GLASS) }

/** Soft shadow for text drawn straight onto the wallpaper. */
val WallpaperText = TextStyle(
    color = Color.White,
    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1.5f), blurRadius = 6f),
)

@Composable
fun PanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF0A84FF),
            surface = SheetColor,
            surfaceContainer = SheetRaised,
            surfaceContainerHigh = SheetRaised,
            background = Color.Black,
        ),
        content = content,
    )
}
