package com.panel.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.LauncherViewModel
import com.panel.launcher.data.NowPlaying

/** Two-way switch shown at the top of the Up next box while music is available. */
@Composable
fun UpNextMusicToggle(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val p = LocalTiles.current
    Row(
        modifier.clip(RoundedCornerShape(50)).background(p.fg.copy(alpha = 0.08f)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(false to "Up next", true to "♫ Playing").forEach { (music, label) ->
            val selected = vm.showMusicTab == music
            Text(
                label.uppercase(),
                color = if (selected) p.fg else p.subtle,
                fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) p.fg.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { vm.showMusicTab = music }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Niagara-style player: art, title/artist and controls. */
@Composable
fun MusicPlayer(vm: LauncherViewModel, np: NowPlaying) {
    val p = LocalTiles.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(p.fg.copy(alpha = 0.1f))
                .clickable { vm.openMusicApp() },
            contentAlignment = Alignment.Center,
        ) {
            val art = np.art
            if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
            else Icon(Icons.Default.MusicNote, null, tint = p.subtle)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.openMusicApp() }) {
                Text(np.title, color = p.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(np.artist, color = p.subtle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height4())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // App-provided extras first (e.g. Spotify's shuffle), at most two.
                np.actions.filter { it.icon != null }.take(2).forEach { a ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable { vm.media.custom(a) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(a.icon!!, a.name, colorFilter = ColorFilter.tint(p.fg), modifier = Modifier.size(20.dp))
                    }
                }
                ControlButton(Icons.Default.SkipPrevious, "Previous", p.fg) { vm.media.previous() }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(p.fg).clickable { vm.media.playPause(np) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (np.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (np.playing) "Pause" else "Play",
                        tint = if (p.fg.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }
                ControlButton(Icons.Default.SkipNext, "Next", p.fg) { vm.media.next() }
            }
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, tint: Color, size: Dp = 36.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint)
    }
}

/** One-line player for the Glance and Big clock layouts, drawn straight on the wallpaper. */
@Composable
fun CompactMusic(vm: LauncherViewModel, style: TextStyle, modifier: Modifier = Modifier) {
    val np = vm.nowPlaying ?: return
    if (!vm.musicAvailable) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "♫ ${np.title}" + (if (np.artist.isNotBlank()) " · ${np.artist}" else ""),
            style = style, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(8.dp)).clickable { vm.openMusicApp() }.padding(4.dp),
        )
        ControlButton(if (np.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/pause", style.color, 34.dp) {
            vm.media.playPause(np)
        }
        ControlButton(Icons.Default.SkipNext, "Next", style.color, 34.dp) { vm.media.next() }
    }
}

private fun Modifier.height4() = this.then(Modifier.padding(top = 4.dp))
