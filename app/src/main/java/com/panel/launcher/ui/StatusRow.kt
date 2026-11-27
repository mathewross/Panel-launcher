package com.panel.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.launcher.data.DeviceStatus

private val Charging = Color(0xFF34C759)

/** Signal bars · Wi-Fi · battery %, drawn to match Panel rather than borrowed system icons. */
@Composable
fun StatusIcons(
    status: DeviceStatus,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    /** Tap the bell to cycle ring / vibrate / silent; long-press for its permission. */
    onRinger: (() -> Unit)? = null,
    onRingerLongPress: (() -> Unit)? = null,
    /** Narrow spaces (the time box): just the bell and battery. */
    compact: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (onRinger != null) {
            val icon = when (status.ringer) {
                android.media.AudioManager.RINGER_MODE_VIBRATE -> Icons.Outlined.Vibration
                android.media.AudioManager.RINGER_MODE_SILENT -> Icons.Outlined.NotificationsOff
                else -> Icons.Outlined.NotificationsActive
            }
            val haptics = LocalHapticFeedback.current
            Box(
                Modifier
                    .clip(CircleShape)
                    .pointerInput(onRinger, onRingerLongPress) {
                        detectTapGestures(
                            onTap = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onRinger() },
                            onLongPress = { onRingerLongPress?.invoke() },
                        )
                    }
                    .padding(4.dp),
            ) { Icon(icon, "Ringer", tint = color, modifier = Modifier.size(size * 1.35f)) }
        }
        if (!compact && status.cell >= 0) SignalBars(status.cell, color, size)
        if (!compact && status.wifi >= 0) WifiArcs(status.wifi, color, size)
        if (status.battery >= 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${status.battery}%", color = color, fontSize = (size.value + 0.5f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(3.dp))
                Battery(status.battery, status.charging, color, size)
            }
        }
    }
}

@Composable
private fun SignalBars(level: Int, color: Color, size: Dp) {
    Canvas(Modifier.size(size * 1.15f, size)) {
        val n = 4
        val gap = this.size.width * 0.08f
        val w = (this.size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val h = this.size.height * (i + 1) / n
            drawRoundRect(
                color.copy(alpha = if (i < level) 1f else 0.3f),
                topLeft = Offset(i * (w + gap), this.size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 3),
            )
        }
    }
}

@Composable
private fun WifiArcs(level: Int, color: Color, size: Dp) {
    Canvas(Modifier.size(size * 1.5f, size)) {
        // A fan of arcs centred on the bottom middle; the largest just touches the top, so nothing is clipped.
        val cx = this.size.width / 2
        val bottom = this.size.height
        val stroke = this.size.height * 0.15f
        val maxR = this.size.height - stroke / 2
        // level 0…4 → dot + up to 3 arcs lit
        drawCircle(color, radius = stroke * 0.75f, center = Offset(cx, bottom - stroke * 0.75f))
        for (i in 1..3) {
            val r = maxR * (0.25f + 0.25f * i)
            drawArc(
                color.copy(alpha = if (level >= i + 1) 1f else 0.3f),
                startAngle = 225f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cx - r, bottom - r), size = Size(r * 2, r * 2),
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun Battery(level: Int, charging: Boolean, color: Color, size: Dp) {
    Canvas(Modifier.size(size * 2f, size)) {
        val bodyW = this.size.width * 0.88f
        val stroke = this.size.height * 0.12f
        drawRoundRect(
            color.copy(alpha = 0.55f), topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyW - stroke, this.size.height - stroke),
            cornerRadius = CornerRadius(this.size.height * 0.28f), style = Stroke(stroke),
        )
        val inset = stroke * 1.6f
        val fillW = (bodyW - inset * 2) * (level.coerceIn(0, 100) / 100f)
        drawRoundRect(
            if (charging) Charging else if (level <= 15) Color(0xFFFF453A) else color,
            topLeft = Offset(inset, inset), size = Size(fillW, this.size.height - inset * 2),
            cornerRadius = CornerRadius(this.size.height * 0.15f),
        )
        drawRoundRect(
            color.copy(alpha = 0.55f), topLeft = Offset(bodyW + stroke * 0.3f, this.size.height * 0.33f),
            size = Size(this.size.width - bodyW - stroke * 0.3f, this.size.height * 0.34f),
            cornerRadius = CornerRadius(stroke),
        )
    }
}
