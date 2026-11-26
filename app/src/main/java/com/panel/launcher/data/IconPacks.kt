package com.panel.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/**
 * Icon styling:
 *  - Icon packs in the common ADW / Nova format (thousands on the Play Store), read from the
 *    pack's appfilter.xml.
 *  - "Themed" icons like the Pixel's: an app's monochrome layer tinted with the phone's
 *    Material You colours (Android 13+; apps without one keep their normal icon).
 */
object IconPacks {
    private val PACK_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
    )

    data class Pack(val packageName: String, val label: String)

    fun installed(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        return PACK_ACTIONS.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        }.map { it.activityInfo.packageName }.distinct().map { pkg ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            Pack(pkg, label)
        }.sortedBy { it.label.lowercase() }
    }

    /** A loaded pack: which drawable to use for which app component. */
    class Loaded(val res: Resources, val pkg: String, private val map: Map<String, String>) {
        fun drawableFor(component: ComponentName): Drawable? {
            val name = map[component.flattenToString()] ?: map[component.flattenToShortString()] ?: return null
            val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: return null
            return runCatching { res.getDrawable(id, null) }.getOrNull()
        }
    }

    fun load(ctx: Context, pkg: String): Loaded? = runCatching {
        val res = ctx.packageManager.getResourcesForApplication(pkg)
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (xmlId != 0) res.getXml(xmlId) else {
            Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "utf-8") }
        }
        val map = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (comp != null && drawable != null && comp.startsWith("ComponentInfo{")) {
                    map[comp.removePrefix("ComponentInfo{").removeSuffix("}")] = drawable
                }
            }
            event = parser.next()
        }
        Loaded(res, pkg, map)
    }.getOrNull()

    /** Pixel-style themed icon, or null if the app has no monochrome layer (or Android < 13). */
    fun themed(ctx: Context, d: Drawable, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val mono = (d as? AdaptiveIconDrawable)?.monochrome ?: return null
        val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bg = ctx.getColor(if (night) android.R.color.system_neutral1_800 else android.R.color.system_accent1_100)
        val fg = ctx.getColor(if (night) android.R.color.system_accent1_100 else android.R.color.system_neutral2_700)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bg)
        val pad = size / 4
        mono.mutate().apply {
            colorFilter = PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN)
            setBounds(-pad, -pad, size + pad, size + pad)
            draw(canvas)
        }
        return bmp
    }

    /** Draws an icon-pack drawable full size (packs ship finished, shaped artwork). */
    fun renderPackIcon(d: Drawable, size: Int): Bitmap {
        if (d is AdaptiveIconDrawable) return AppRepository.renderIcon(d, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }
}
