package com.panel.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class HeaderStyle { BOXES, GLANCE, CLOCK }
enum class TileStyle { GLASS, LIGHT, DARK }
enum class DockPosition { RIGHT, LEFT, BOTTOM }

/** Persistent settings, exposed as Compose state so the UI updates as soon as they change. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("panel_settings", Context.MODE_PRIVATE)

    private class Pref<T>(
        private val sp: SharedPreferences,
        private val key: String,
        initial: T,
        private val write: SharedPreferences.Editor.(String, T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(initial)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            sp.edit().apply { write(key, value) }.apply()
        }
    }

    private fun bool(key: String, def: Boolean) =
        Pref(sp, key, sp.getBoolean(key, def)) { k, v -> putBoolean(k, v) }

    private fun int(key: String, def: Int) =
        Pref(sp, key, sp.getInt(key, def)) { k, v -> putInt(k, v) }

    private fun long(key: String, def: Long) =
        Pref(sp, key, sp.getLong(key, def)) { k, v -> putLong(k, v) }

    private fun string(key: String, def: String) =
        Pref(sp, key, sp.getString(key, def) ?: def) { k, v -> putString(k, v) }

    private fun list(key: String) = Pref(
        sp, key,
        sp.getString(key, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
    ) { k, v -> putString(k, v.joinToString("\n")) }

    var initialized by bool("initialized", false)

    // Home layout
    var favorites: List<String> by list("favorites")
    var dock: List<String> by list("dock")
    private var headerStyleName by string("header_style", HeaderStyle.BOXES.name)
    private var tileStyleName by string("tile_style", TileStyle.GLASS.name)
    var columns by int("columns", 4)
    var iconRoundness by int("icon_roundness", 50) // percent of icon size; 50 = circle
    private var dockPositionName by string("dock_position", DockPosition.RIGHT.name)
    var showUpNext by bool("show_up_next", true)
    var dockHeight by int("dock_height", 0)              // 0 = top … 100 = bottom (side dock only)
    var upNextHiddenCalendars: List<String> by list("up_next_hidden_calendars") // calendar ids
    var filterAgenda by bool("filter_agenda", false)
    var upNextAllDay by bool("up_next_all_day", true)
    var showMusic by bool("show_music", true)
    var foldersJson by string("folders", "{}")
    var showRecents by bool("show_recents", true)
    var hideStatusBar by bool("hide_status_bar", false)
    var lockPortrait by bool("lock_portrait", true)
    var pixelFont by bool("pixel_font", true)
    var swipeDownShade by bool("swipe_down_shade", true)
    var swipeUpPanel by bool("swipe_up_panel", true)
    var openAnimation by bool("open_animation", true)
    var boxNumberScale by int("box_number_scale", 100)   // percent
    var boxNumberWeight by int("box_number_weight", 500) // 300 light … 900 black
    var boxNumberFont by string("box_number_font", "default")
    var recentApps: List<String> by list("recent_apps") // most recent first, launched via Panel
    var wallpaperTone by string("wallpaper_tone", "auto") // auto | light | dark
    var iconScale by int("icon_scale", 100) // percent

    // Big clock (iOS lock-screen style)
    var clockSize by int("clock_size", 120)        // sp
    var clockStretch by int("clock_stretch", 150)  // percent of normal height
    var clockOffset by int("clock_offset", 10)     // dp down from the top
    var clockOpacity by int("clock_opacity", 85)   // percent
    var clockColor by int("clock_color", 0xFFFFFFFF.toInt())
    var clockFont by string("clock_font", "condensed")
    var depthEffect by bool("depth_effect", true)
    var showLabels by bool("show_labels", true)
    var sidebarOnLeft by bool("sidebar_left", false)
    var showAlarm by bool("show_alarm", true)

    // Weather
    var fahrenheit by bool("fahrenheit", false)
    var city by string("city", "")          // empty = use device location
    var cityLat by string("city_lat", "")
    var cityLon by string("city_lon", "")
    var weatherCache by string("weather_cache", "")

    // Tap targets (app keys, empty = system default)
    var weatherApp by string("weather_app", "")
    var clockApp by string("clock_app", "")
    var calendarApp by string("calendar_app", "")

    var lastWeatherAttempt by long("weather_attempt", 0L)

    var headerStyle: HeaderStyle
        get() = runCatching { HeaderStyle.valueOf(headerStyleName) }.getOrDefault(HeaderStyle.BOXES)
        set(v) { headerStyleName = v.name }

    var dockPosition: DockPosition
        get() = runCatching { DockPosition.valueOf(dockPositionName) }.getOrDefault(DockPosition.RIGHT)
        set(v) { dockPositionName = v.name }

    var tileStyle: TileStyle
        get() = runCatching { TileStyle.valueOf(tileStyleName) }.getOrDefault(TileStyle.GLASS)
        set(v) { tileStyleName = v.name }
}
