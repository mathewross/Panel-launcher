package com.panel.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Telephony
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
) {
    val packageName: String get() = component.packageName
}

data class AppShortcut(val info: android.content.pm.ShortcutInfo, val label: String, val icon: ImageBitmap?)

/**
 * Reads installed apps through the regular LauncherApps API that every home-screen app uses.
 * No accessibility service, no QUERY_ALL_PACKAGES.
 */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconPx = (context.resources.displayMetrics.density * 64).toInt().coerceAtLeast(96)

    /**
     * [iconStyle]: "original", "themed" (Pixel-style monochrome icons) or "pack" (use [iconPack]).
     */
    suspend fun load(
        iconStyle: String = "original",
        iconPack: String = "",
        softBackgrounds: Boolean = false,
    ): List<AppEntry> = withContext(Dispatchers.IO) {
        val pack = if (iconStyle == "pack" && iconPack.isNotBlank()) IconPacks.load(context, iconPack) else null
        fun iconFor(info: android.content.pm.LauncherActivityInfo): android.graphics.Bitmap {
            val original = info.getIcon(0)
            pack?.drawableFor(info.componentName)?.let { return IconPacks.renderPackIcon(it, iconPx) }
            if (iconStyle == "themed") IconPacks.themed(context, original, iconPx)?.let { return it }
            return renderIcon(original, iconPx, softBackgrounds)
        }
        userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user)
                .filter { it.componentName.packageName != context.packageName }
                .map { info ->
                    AppEntry(
                        key = info.componentName.flattenToString() + "#" + serial,
                        label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                        component = info.componentName,
                        user = user,
                        icon = iconFor(info).asImageBitmap(),
                    )
                }
        }.sortedBy { it.label.lowercase() }
    }

    fun launch(app: AppEntry, sourceBounds: android.graphics.Rect? = null, options: android.os.Bundle? = null) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, sourceBounds, options) }
    }

    /** The app's own quick actions (needs Panel to be the default home app; no permission). */
    fun shortcuts(app: AppEntry): List<AppShortcut> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val dpi = context.resources.displayMetrics.densityDpi
        launcherApps.getShortcuts(q, app.user).orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(5)
            .map { info ->
                val icon = runCatching {
                    launcherApps.getShortcutIconDrawable(info, dpi)?.let { d ->
                        val b = android.graphics.Bitmap.createBitmap(72, 72, android.graphics.Bitmap.Config.ARGB_8888)
                        d.setBounds(0, 0, 72, 72); d.draw(Canvas(b)); b.asImageBitmap()
                    }
                }.getOrNull()
                AppShortcut(info, (info.shortLabel ?: info.longLabel ?: "").toString(), icon)
            }
    }.getOrDefault(emptyList())

    fun startShortcut(s: AppShortcut) {
        runCatching { launcherApps.startShortcut(s.info, null, null) }
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun watch(onChange: () -> Unit): LauncherApps.Callback {
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()
            override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()
            override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()
            override fun onPackagesAvailable(p: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(p: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
        }
        launcherApps.registerCallback(cb, Handler(Looper.getMainLooper()))
        return cb
    }

    fun unwatch(cb: LauncherApps.Callback) = launcherApps.unregisterCallback(cb)

    /** Package names of the default phone, messages, browser and camera apps, for a starter dock. */
    fun defaultDockPackages(): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        return intents.mapIndexedNotNull { i, intent ->
            if (i == 1 && sms != null) sms
            else pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeIf { it != "android" }
        }.distinct()
    }

    companion object {
        /** Draws any icon as a full-bleed square; the UI clips it to the chosen rounded shape. */
        /**
         * [softBackground]: replace the icon's background (or the white plate behind old-style
         * icons) with a gentle tint of the icon's own colour, so icons sit together better.
         */
        fun renderIcon(d: Drawable, size: Int, softBackground: Boolean = false): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            if (d is AdaptiveIconDrawable) {
                // Adaptive layers are 108dp with the visible area being the middle 72dp (2/3).
                val pad = size / 4
                val bg = d.background
                if (softBackground && bg != null) canvas.drawColor(soften(averageColor(bg)))
                else bg?.apply { setBounds(-pad, -pad, size + pad, size + pad); draw(canvas) }
                d.foreground?.apply { setBounds(-pad, -pad, size + pad, size + pad); draw(canvas) }
            } else {
                canvas.drawColor(if (softBackground) soften(averageColor(d)) else Color.WHITE)
                val inset = size / 7
                d.setBounds(inset, inset, size - inset, size - inset)
                d.draw(canvas)
            }
            return bmp
        }

        /** Average colour of the opaque parts of a drawable. */
        private fun averageColor(d: Drawable): Int {
            val small = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 16, 16)
            d.draw(Canvas(small))
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (y in 0 until 16) for (x in 0 until 16) {
                val c = small.getPixel(x, y)
                if (Color.alpha(c) < 128) continue
                r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
            }
            return if (n == 0L) Color.WHITE else Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }

        /** A soft pastel version of a colour: same hue, gentler saturation, light. */
        private fun soften(c: Int): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(c, hsv)
            hsv[1] = hsv[1] * 0.45f
            hsv[2] = 0.93f
            return Color.HSVToColor(hsv)
        }
    }
}
