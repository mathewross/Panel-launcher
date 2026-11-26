package com.panel.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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

fun paletteFor(style: TileStyle, lightWallpaper: Boolean) = when (style) {
    // Glass over a light wallpaper: a faint dark tint with dark text, so it stays readable.
    TileStyle.GLASS -> if (lightWallpaper) TilePalette(
        bg = Color(0x17000000),
        fg = Color(0xFF1C1C1E), subtle = Color(0xC21C1C1E), border = Color(0x1F000000),
        glass = true,
    ) else TilePalette(
        bg = Color(0x3DFFFFFF),
        fg = Color.White, subtle = Color.White.copy(alpha = 0.82f), border = Color.White.copy(alpha = 0.28f),
        glass = true,
    )
    TileStyle.LIGHT -> TilePalette(Color(0xF5FFFFFF), Color(0xFF111111), Color(0xFF6E6E73), Color.Transparent)
    TileStyle.DARK -> TilePalette(Color(0xEB1C1C1E), Color.White, Color(0xFF9A9AA0), Color.Transparent)
}

val LocalTiles = staticCompositionLocalOf { paletteFor(TileStyle.GLASS, false) }

/** White text with a soft dark shadow, for dark wallpapers. */
val TextOnDarkWallpaper = TextStyle(
    color = Color.White,
    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1.5f), blurRadius = 6f),
)

/** Near-black text with a soft light glow, for light wallpapers. */
val TextOnLightWallpaper = TextStyle(
    color = Color(0xFF1C1C1E),
    shadow = Shadow(Color.White.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 6f),
)

val LocalWallpaperText = staticCompositionLocalOf { TextOnDarkWallpaper }

/** Style for anything drawn straight onto the wallpaper; follows the wallpaper's brightness. */
val WallpaperText: TextStyle
    @Composable @ReadOnlyComposable get() = LocalWallpaperText.current

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
