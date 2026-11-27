package com.panel.launcher.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager

/** Battery and connection levels for the home screen's little status row. Levels are 0…4, -1 = off/none. */
data class DeviceStatus(
    val battery: Int = -1,
    val charging: Boolean = false,
    val wifi: Int = -1,
    val cell: Int = -1,
    /** AudioManager.RINGER_MODE_NORMAL / VIBRATE / SILENT */
    val ringer: Int = android.media.AudioManager.RINGER_MODE_NORMAL,
) {
    companion object {
        /** Reads everything at once. No permissions beyond the normal network-state one. */
        fun read(ctx: Context): DeviceStatus {
            val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = bat?.let {
                val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && s > 0) l * 100 / s else -1
            } ?: -1
            val plugged = (bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

            val wifi = runCatching {
                val cm = ctx.getSystemService(ConnectivityManager::class.java)
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    val rssi = if (Build.VERSION.SDK_INT >= 29) caps.signalStrength else Int.MIN_VALUE
                    @Suppress("DEPRECATION")
                    if (rssi == Int.MIN_VALUE) 4 else WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4)
                } else -1
            }.getOrDefault(-1)

            val cell = runCatching {
                val tm = ctx.getSystemService(TelephonyManager::class.java)
                if (tm.simState != TelephonyManager.SIM_STATE_READY) -1
                else if (Build.VERSION.SDK_INT >= 28) tm.signalStrength?.level?.coerceIn(0, 4) ?: -1 else -1
            }.getOrDefault(-1)

            val ringer = runCatching { ctx.getSystemService(android.media.AudioManager::class.java).ringerMode }
                .getOrDefault(android.media.AudioManager.RINGER_MODE_NORMAL)
            return DeviceStatus(level, plugged, wifi, cell, ringer)
        }
    }
}
