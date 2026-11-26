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
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.panel.launcher.data.TileStyle

val Accent = Color(0xFFFF453A)       // iOS-style red used for dates / "Up next"
val SheetColor = Color(0xFF1C1C1E)
val SheetRaised = Color(0xFF2C2C2E)

/**
 * Colours for boxes, dock and bars. [bg] is used when there's no wallpaper to blur;
 * [glassTint] is laid over the blurred wallpaper when there is.
 */
data class TilePalette(
    val bg: Color,
    val fg: Color,
    val subtle: Color,
    val border: Color,
    val glass: Boolean = false,
    val light: Boolean = false,
    val glassTint: Color = bg,
)

fun paletteFor(style: TileStyle, lightWallpaper: Boolean) = when (style) {
    // Glass over a light wallpaper: frosted white with dark text.
    TileStyle.GLASS -> if (lightWallpaper) TilePalette(
        bg = Color(0x1A000000),
        fg = Color(0xFF1C1C1E), subtle = Color(0xB31C1C1E), border = Color(0x33FFFFFF),
        glass = true, light = true, glassTint = Color(0x59FFFFFF),
    ) else TilePalette(
        bg = Color(0x3DFFFFFF),
        fg = Color.White, subtle = Color.White.copy(alpha = 0.8f), border = Color.White.copy(alpha = 0.22f),
        glass = true, light = false, glassTint = Color(0x29FFFFFF),
    )
    TileStyle.LIGHT -> TilePalette(Color(0xF5FFFFFF), Color(0xFF111111), Color(0xFF6E6E73), Color(0x14000000), light = true)
    TileStyle.DARK -> TilePalette(Color(0xEB1C1C1E), Color.White, Color(0xFF9A9AA0), Color(0x1FFFFFFF))
}

/** Google Sans on Pixels (the font the Pixel's own UI uses); falls back to Roboto elsewhere. */
private fun deviceFamily(name: String) = FontFamily(
    listOf(300, 400, 500, 600, 700).map { Font(DeviceFontFamilyName(name), FontWeight(it)) },
)
val PixelSans: FontFamily = deviceFamily("google-sans-text")
val PixelDisplay: FontFamily = deviceFamily("google-sans")

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
fun PanelTheme(pixelFont: Boolean = true, content: @Composable () -> Unit) {
    val base = androidx.compose.material3.Typography()
    val family = if (pixelFont) PixelSans else FontFamily.Default
    val typography = androidx.compose.material3.Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
    )
    MaterialTheme(
        typography = typography,
        colorScheme = darkColorScheme(
            primary = Color(0xFF0A84FF),
            surface = SheetColor,
            surfaceContainer = SheetRaised,
            surfaceContainerHigh = SheetRaised,
            background = Color.Black,
        ),
    ) {
        androidx.compose.material3.ProvideTextStyle(TextStyle(fontFamily = family), content)
    }
}
