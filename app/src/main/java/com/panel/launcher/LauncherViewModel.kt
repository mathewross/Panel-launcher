package com.panel.launcher

import android.app.AlarmManager
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panel.launcher.data.AppEntry
import com.panel.launcher.data.AppRepository
import com.panel.launcher.data.CalEvent
import com.panel.launcher.data.CalendarRepository
import com.panel.launcher.data.Prefs
import com.panel.launcher.data.Weather
import com.panel.launcher.data.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which app a header tile should open when the user hasn't chosen one yet. */
enum class PickTarget { WEATHER, CLOCK, CALENDAR }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    val prefs = Prefs(app)
    val repo = AppRepository(app)

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var appsByKey by mutableStateOf<Map<String, AppEntry>>(emptyMap()); private set
    var now by mutableLongStateOf(System.currentTimeMillis()); private set
    var events by mutableStateOf<List<CalEvent>>(emptyList()); private set
    var hasCalendar by mutableStateOf(CalendarRepository.hasPermission(app)); private set
    var weather by mutableStateOf(Weather.fromJson(prefs.weatherCache)); private set
    var weatherLoading by mutableStateOf(false); private set
    var weatherError by mutableStateOf<String?>(null); private set
    var nextAlarm by mutableStateOf<Long?>(null); private set

    // Custom wallpaper + subject cut-out for the depth-effect clock
    private var wallpaperBitmap: android.graphics.Bitmap? = null
    var wallpaper by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null); private set
    var wallpaperSubject by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null); private set
    var wallpaperStatus by mutableStateOf<String?>(null); private set

    /** True when the wallpaper behind the home screen is light, so text and icons should be dark. */
    private var detectedLight by mutableStateOf(false)
    val lightWallpaper: Boolean
        get() = when (prefs.wallpaperTone) {
            "light" -> true
            "dark" -> false
            else -> detectedLight
        }
    private val wallpaperColorsListener =
        android.app.WallpaperManager.OnColorsChangedListener { _, _ -> detectWallpaperTone() }

    private val appsCallback: LauncherApps.Callback
    private var calendarJob: Job? = null
    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshCalendar()
    }
    private var observingCalendar = false

    init {
        viewModelScope.launch {
            val (w, f) = com.panel.launcher.data.CustomWallpaper.load(ctx)
            wallpaperBitmap = w
            wallpaper = w?.asImageBitmap()
            wallpaperSubject = f?.asImageBitmap()
            detectWallpaperTone()
        }
        runCatching {
            android.app.WallpaperManager.getInstance(app)
                .addOnColorsChangedListener(wallpaperColorsListener, Handler(Looper.getMainLooper()))
        }
        refreshApps()
        appsCallback = repo.watch { refreshApps() }
        viewModelScope.launch {
            var ticks = 0
            while (true) {
                now = System.currentTimeMillis()
                refreshAlarm()
                if (ticks++ % 5 == 0) refreshCalendar()
                if (ticks % 30 == 0) refreshWeather()
                delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            }
        }
    }

    fun onResume() {
        now = System.currentTimeMillis()
        detectWallpaperTone()
        refreshAlarm()
        refreshCalendar()
        refreshWeather()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val list = repo.load()
            apps = list
            appsByKey = list.associateBy { it.key }
            if (!prefs.initialized && list.isNotEmpty()) {
                prefs.initialized = true
                val dockPkgs = repo.defaultDockPackages()
                prefs.dock = dockPkgs.mapNotNull { pkg -> list.firstOrNull { it.packageName == pkg }?.key }
            }
        }
    }

    fun refreshCalendar() {
        hasCalendar = CalendarRepository.hasPermission(ctx)
        if (!hasCalendar) { events = emptyList(); return }
        if (!observingCalendar) {
            runCatching {
                ctx.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, calendarObserver)
                observingCalendar = true
            }
        }
        calendarJob?.cancel()
        calendarJob = viewModelScope.launch { events = CalendarRepository.upcoming(ctx) }
    }

    private fun refreshAlarm() {
        if (!prefs.showAlarm) { nextAlarm = null; return }
        nextAlarm = ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    }

    /** Refreshes weather if the cached reading is older than 30 minutes (or [force]). */
    fun refreshWeather(force: Boolean = false) {
        val cached = weather
        val stale = cached == null || System.currentTimeMillis() - cached.fetchedAt > 30 * 60_000
        if (weatherLoading || (!force && !stale)) return
        if (!force && System.currentTimeMillis() - prefs.lastWeatherAttempt < 5 * 60_000) return
        val useCity = prefs.cityLat.isNotBlank() && prefs.cityLon.isNotBlank()
        if (!useCity && !WeatherRepository.hasLocationPermission(ctx)) return
        prefs.lastWeatherAttempt = System.currentTimeMillis()
        weatherLoading = true
        viewModelScope.launch {
            runCatching {
                val w = if (useCity) {
                    WeatherRepository.fetch(prefs.cityLat.toDouble(), prefs.cityLon.toDouble(), prefs.city)
                } else {
                    val loc = WeatherRepository.deviceLocation(ctx)
                        ?: error("Location unavailable — set a city in settings")
                    WeatherRepository.fetch(loc.latitude, loc.longitude, WeatherRepository.placeName(ctx, loc))
                }
                weather = w
                prefs.weatherCache = w.toJson()
                weatherError = null
            }.onFailure { weatherError = it.message ?: "Couldn't load weather" }
            weatherLoading = false
        }
    }

    /** Looks up a city name and switches weather to it. Returns an error message or null. */
    suspend fun setCity(name: String): String? {
        if (name.isBlank()) {
            prefs.city = ""; prefs.cityLat = ""; prefs.cityLon = ""
            refreshWeather(force = true)
            return null
        }
        val place = runCatching { WeatherRepository.geocode(name) }.getOrElse { return "Network error" }
            ?: return "Couldn't find \"$name\""
        prefs.city = place.name
        prefs.cityLat = place.lat.toString()
        prefs.cityLon = place.lon.toString()
        refreshWeather(force = true)
        return null
    }

    // ---------- Custom wallpaper ----------

    /**
     * Works out whether the wallpaper is light. Uses Panel's own photo if one is set, otherwise
     * the colour hints Android publishes for the system wallpaper (no permission needed).
     */
    fun detectWallpaperTone() {
        val own = wallpaperBitmap
        if (own != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                // Average brightness of the top half, where the boxes and clock sit.
                val small = android.graphics.Bitmap.createScaledBitmap(own, 24, 48, true)
                var sum = 0.0
                for (y in 0 until 24) for (x in 0 until 24) {
                    val c = small.getPixel(x, y)
                    sum += 0.2126 * android.graphics.Color.red(c) + 0.7152 * android.graphics.Color.green(c) +
                        0.0722 * android.graphics.Color.blue(c)
                }
                val light = sum / (24 * 24) / 255.0 > 0.62
                launch(kotlinx.coroutines.Dispatchers.Main) { detectedLight = light }
            }
            return
        }
        val colors = runCatching {
            android.app.WallpaperManager.getInstance(ctx).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return
        detectedLight = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        } else {
            colors.primaryColor.luminance() > 0.6f
        }
    }

    fun importWallpaper(uri: Uri) {
        viewModelScope.launch {
            wallpaperStatus = "Loading photo…"
            val bmp = runCatching { com.panel.launcher.data.CustomWallpaper.import(ctx, uri) }.getOrElse {
                wallpaperStatus = "Couldn't open that photo"
                return@launch
            }
            wallpaperBitmap = bmp
            wallpaper = bmp.asImageBitmap()
            wallpaperSubject = null
            detectWallpaperTone()
            wallpaperStatus = "Finding the subject for the depth effect…"
            val fg = runCatching { com.panel.launcher.data.CustomWallpaper.segment(ctx, bmp) }.getOrNull()
            wallpaperSubject = fg?.asImageBitmap()
            wallpaperStatus = if (fg != null) "Depth effect ready"
            else "No clear subject found — the clock will sit on top of the photo"
        }
    }

    fun clearWallpaper() {
        com.panel.launcher.data.CustomWallpaper.clear(ctx)
        wallpaperBitmap = null
        wallpaper = null
        wallpaperSubject = null
        wallpaperStatus = null
        detectWallpaperTone()
    }

    fun useAsSystemWallpaper() {
        val bmp = wallpaperBitmap ?: return
        viewModelScope.launch {
            runCatching { com.panel.launcher.data.CustomWallpaper.setAsSystemWallpaper(ctx, bmp) }
                .onSuccess { toast("Set as phone wallpaper") }
                .onFailure { toast("Couldn't set wallpaper") }
        }
    }

    // ---------- Home / dock editing ----------

    fun isFavorite(app: AppEntry) = app.key in prefs.favorites
    fun isDocked(app: AppEntry) = app.key in prefs.dock

    fun toggleFavorite(app: AppEntry) {
        prefs.favorites = if (isFavorite(app)) prefs.favorites - app.key else prefs.favorites + app.key
    }

    fun toggleDock(app: AppEntry) {
        prefs.dock = when {
            isDocked(app) -> prefs.dock - app.key
            prefs.dock.size >= dockLimit -> { toast("The dock holds $dockLimit apps"); prefs.dock }
            else -> prefs.dock + app.key
        }
    }

    val dockLimit: Int
        get() = if (prefs.dockPosition == com.panel.launcher.data.DockPosition.BOTTOM) 5 else 6

    fun moveFavorite(app: AppEntry, by: Int) { prefs.favorites = move(prefs.favorites, app.key, by) }
    fun moveDock(app: AppEntry, by: Int) { prefs.dock = move(prefs.dock, app.key, by) }

    private fun move(list: List<String>, key: String, by: Int): List<String> {
        val i = list.indexOf(key)
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return list
        return list.toMutableList().apply { add(j, removeAt(i)) }
    }

    // ---------- Actions ----------

    fun launch(app: AppEntry) = repo.launch(app)

    /** Returns false when no app is configured and nothing sensible could be opened. */
    fun openClock(context: Context): Boolean {
        appsByKey[prefs.clockApp]?.let { launch(it); return true }
        if (start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))) return true
        ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.showIntent?.let {
            runCatching { it.send(); return true }
        }
        return false
    }

    fun openCalendar(context: Context): Boolean {
        appsByKey[prefs.calendarApp]?.let { launch(it); return true }
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .appendPath(System.currentTimeMillis().toString()).build()
        return start(context, Intent(Intent.ACTION_VIEW, uri)) ||
            start(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
    }

    fun openWeather(): Boolean {
        appsByKey[prefs.weatherApp]?.let { launch(it); return true }
        // Default to a known weather app if one is installed (Pixel Weather first).
        val known = listOf(
            "com.google.android.apps.weather",
            "com.samsung.android.weather",
            "com.sec.android.daemonapp",
            "com.accuweather.android",
            "com.weather.Weather",
        )
        known.firstNotNullOfOrNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
            ?.let { launch(it); return true }
        return false
    }

    fun openEvent(context: Context, e: CalEvent) {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
        if (!start(context, intent)) openCalendar(context)
    }

    fun newEvent(context: Context) {
        start(context, Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI))
    }

    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (e: Exception) {
        false
    }

    fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    override fun onCleared() {
        runCatching { android.app.WallpaperManager.getInstance(ctx).removeOnColorsChangedListener(wallpaperColorsListener) }
        repo.unwatch(appsCallback)
        if (observingCalendar) ctx.contentResolver.unregisterContentObserver(calendarObserver)
    }
}
