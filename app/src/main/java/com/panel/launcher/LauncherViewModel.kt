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

/** Marks an empty cell in the home grid. */
const val EMPTY_SLOT = "-"

/** Grid slots holding a folder are "folder:<id>". */
const val FOLDER_PREFIX = "folder:"

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
    var calendars by mutableStateOf<List<com.panel.launcher.data.CalendarInfo>>(emptyList()); private set

    /** Events from the calendars chosen for "Up next" (Settings › Up next calendars). */
    val upNextEvents: List<CalEvent>
        get() {
            val hidden = prefs.upNextHiddenCalendars.toSet()
            val allDay = prefs.upNextAllDay
            return events.filter { (allDay || !it.allDay) && it.calendarId.toString() !in hidden }
        }

    /** Events for the agenda sheet: everything, unless the agenda is set to follow the same filter. */
    val agendaEvents: List<CalEvent>
        get() = if (prefs.filterAgenda) upNextEvents else events

    fun setCalendarInUpNext(id: Long, show: Boolean) {
        val key = id.toString()
        prefs.upNextHiddenCalendars =
            if (show) prefs.upNextHiddenCalendars - key else (prefs.upNextHiddenCalendars + key).distinct()
    }
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

    /** Small blurred copy of whichever wallpaper Panel can see, for frosted-glass surfaces. */
    var glass by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null); private set
    var canReadSystemWallpaper by mutableStateOf(false); private set

    private var glassKey: String? = null

    private fun refreshGlass() {
        val allFiles = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            android.os.Environment.isExternalStorageManager()
        val wallpaperId = runCatching {
            android.app.WallpaperManager.getInstance(ctx).getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)
        }.getOrDefault(0)
        // Only rebuild when the wallpaper (or our access to it) actually changed.
        val key = "${System.identityHashCode(wallpaperBitmap)}-$wallpaperId-$allFiles"
        if (key == glassKey) return
        glassKey = key
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val source = wallpaperBitmap ?: if (allFiles) runCatching {
                @Suppress("MissingPermission")
                val d = android.app.WallpaperManager.getInstance(ctx).drawable
                (d as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: d?.let { androidx.core.graphics.drawable.DrawableCompat.wrap(it) }
                        ?.let { dr -> android.graphics.Bitmap.createBitmap(540, 1170, android.graphics.Bitmap.Config.ARGB_8888)
                            .also { b -> dr.setBounds(0, 0, 540, 1170); dr.draw(android.graphics.Canvas(b)) } }
            }.getOrNull() else null
            val blurred = source?.let { com.panel.launcher.data.GlassSource.from(it).asImageBitmap() }
            launch(kotlinx.coroutines.Dispatchers.Main) {
                canReadSystemWallpaper = allFiles
                glass = blurred
            }
        }
    }

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

    // ---------- Music ----------
    var nowPlaying by mutableStateOf<com.panel.launcher.data.NowPlaying?>(null); private set
    private var lastPlayingAt by mutableLongStateOf(0L)
    /** Which view the Up next box shows while music is available. */
    var showMusicTab by mutableStateOf(false)
    var hasNotificationAccess by mutableStateOf(false); private set

    /** Music controls are offered while something plays, and for 10 minutes after it pauses. */
    val musicAvailable: Boolean
        get() {
            val np = nowPlaying ?: return false
            return prefs.showMusic && (np.playing || now - lastPlayingAt < 10 * 60_000)
        }

    val media = com.panel.launcher.data.MediaRepository(app, viewModelScope) { np ->
        val wasPlaying = nowPlaying?.playing == true
        nowPlaying = np
        if (np?.playing == true) {
            lastPlayingAt = System.currentTimeMillis()
            if (!wasPlaying) showMusicTab = true // pop open when playback starts, like Niagara
        }
    }

    fun openMusicApp() {
        val pkg = nowPlaying?.packageName ?: return
        apps.firstOrNull { it.packageName == pkg }?.let { launch(it) }
    }

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
        media.start()
        hasNotificationAccess = media.hasNotificationAccess()
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
        resumed = true
        resumeTick++
        now = System.currentTimeMillis()
        media.refreshMode()
        hasNotificationAccess = media.hasNotificationAccess()
        detectWallpaperTone()
        refreshAlarm()
        refreshCalendar()
        refreshWeather()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val list = repo.load(prefs.iconStyle, prefs.iconPack, prefs.softIconBackgrounds)
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
        calendarJob = viewModelScope.launch {
            events = CalendarRepository.upcoming(ctx)
            calendars = CalendarRepository.calendars(ctx)
        }
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
        refreshGlass()
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
            // Use the same photo as the phone's wallpaper so the lock screen and app transitions match.
            if (prefs.syncPhoneWallpaper) {
                runCatching { com.panel.launcher.data.CustomWallpaper.setAsSystemWallpaper(ctx, bmp) }
            }
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

    // ---------- Home grid slots & folders ----------
    //
    // Home apps are stored as grid slots in reading order. A slot holds an app key, a folder
    // token ("folder:<id>") or EMPTY_SLOT, so things can sit anywhere with gaps between.

    var folders by mutableStateOf(com.panel.launcher.data.Folders.load(prefs.foldersJson)); private set

    private fun saveFolders(map: Map<String, com.panel.launcher.data.Folder>) {
        folders = map
        prefs.foldersJson = com.panel.launcher.data.Folders.save(map)
    }

    fun folderOf(slot: String): com.panel.launcher.data.Folder? =
        if (slot.startsWith(FOLDER_PREFIX)) folders[slot.removePrefix(FOLDER_PREFIX)] else null

    fun folderApps(f: com.panel.launcher.data.Folder): List<AppEntry> = f.apps.mapNotNull { appsByKey[it] }

    /** True when a slot has something to show (an installed app, or a folder with installed apps). */
    fun occupied(slot: String?): Boolean = when {
        slot == null || slot == EMPTY_SLOT -> false
        slot.startsWith(FOLDER_PREFIX) -> folderOf(slot)?.let { folderApps(it).isNotEmpty() } == true
        else -> slot in appsByKey
    }

    fun isFavorite(app: AppEntry) =
        app.key in prefs.favorites || folders.values.any { app.key in it.apps }

    fun isDocked(app: AppEntry) = app.key in prefs.dock

    private fun placeInFirstGap(slots: MutableList<String>, item: String) {
        val gap = slots.indexOfFirst { !occupied(it) }
        if (gap >= 0) slots[gap] = item else slots += item
    }

    fun trimSlots(slots: List<String>): List<String> {
        var end = slots.size
        while (end > 0 && !occupied(slots[end - 1])) end--
        return slots.subList(0, end).toList()
    }

    /** Add to home (first free cell), or remove from home / its folder. */
    fun toggleFavorite(app: AppEntry) {
        val slots = prefs.favorites.toMutableList()
        val inFolder = folders.values.firstOrNull { app.key in it.apps }
        when {
            inFolder != null -> { removeFromFolder(inFolder.id, app.key, backToHome = false); return }
            app.key in slots -> slots[slots.indexOf(app.key)] = EMPTY_SLOT
            else -> placeInFirstGap(slots, app.key)
        }
        prefs.favorites = trimSlots(slots)
    }

    /**
     * Drop the thing from cell [from] onto cell [to]. Empty cell: move it there. App onto app:
     * make a folder. App onto folder: add it. Folder onto anything: swap. Returns the new slots.
     */
    fun dropSlot(current: List<String>, from: Int, to: Int): List<String> {
        val slots = current.toMutableList()
        while (slots.size <= maxOf(from, to)) slots += EMPTY_SLOT
        val moving = slots[from]
        val target = slots[to]
        val movingIsApp = moving in appsByKey
        when {
            !occupied(target) -> { slots[to] = moving; slots[from] = EMPTY_SLOT }
            movingIsApp && target in appsByKey -> {
                val id = System.currentTimeMillis().toString(36)
                saveFolders(folders + (id to com.panel.launcher.data.Folder(id, "Folder", listOf(target, moving))))
                slots[to] = FOLDER_PREFIX + id
                slots[from] = EMPTY_SLOT
            }
            movingIsApp && folderOf(target) != null -> {
                val f = folderOf(target)!!
                saveFolders(folders + (f.id to f.copy(apps = f.apps + moving)))
                slots[from] = EMPTY_SLOT
            }
            else -> { slots[to] = moving; slots[from] = target }
        }
        val result = trimSlots(slots)
        prefs.favorites = result
        return result
    }

    /** Puts [appKey] in folder [folderId] (or a new folder when null), taking it off the grid. */
    fun addToFolder(folderId: String?, appKey: String, newName: String = "Folder") {
        val slots = prefs.favorites.toMutableList()
        val wasAt = slots.indexOf(appKey)
        if (wasAt >= 0) slots[wasAt] = EMPTY_SLOT
        // Leave any other folder it was in.
        var map = folders.mapValues { (_, f) -> if (appKey in f.apps) f.copy(apps = f.apps - appKey) else f }
        if (folderId != null && map[folderId] != null) {
            val f = map.getValue(folderId)
            map = map + (folderId to f.copy(apps = f.apps + appKey))
        } else {
            val id = System.currentTimeMillis().toString(36)
            map = map + (id to com.panel.launcher.data.Folder(id, newName, listOf(appKey)))
            if (wasAt >= 0) slots[wasAt] = FOLDER_PREFIX + id else placeInFirstGap(slots, FOLDER_PREFIX + id)
        }
        saveFolders(map)
        prefs.favorites = trimSlots(cleanupFolders(slots))
    }

    fun removeFromFolder(folderId: String, appKey: String, backToHome: Boolean = true) {
        val f = folders[folderId] ?: return
        saveFolders(folders + (folderId to f.copy(apps = f.apps - appKey)))
        val slots = prefs.favorites.toMutableList()
        if (backToHome) placeInFirstGap(slots, appKey)
        prefs.favorites = trimSlots(cleanupFolders(slots))
    }

    fun renameFolder(folderId: String, name: String) {
        val f = folders[folderId] ?: return
        saveFolders(folders + (folderId to f.copy(name = name)))
    }

    /** Ungroup: the folder's apps go back onto the grid, starting where the folder was. */
    fun dissolveFolder(folderId: String) {
        val f = folders[folderId] ?: return
        val slots = prefs.favorites.toMutableList()
        val at = slots.indexOf(FOLDER_PREFIX + folderId)
        val apps = f.apps.filter { it in appsByKey }
        if (at >= 0) slots[at] = EMPTY_SLOT
        saveFolders(folders - folderId)
        apps.forEachIndexed { i, key -> if (i == 0 && at >= 0) slots[at] = key else placeInFirstGap(slots, key) }
        prefs.favorites = trimSlots(slots)
    }

    /** A folder with one app becomes that app; an empty one disappears. */
    private fun cleanupFolders(slots: MutableList<String>): MutableList<String> {
        var map = folders
        for (i in slots.indices) {
            val f = folderOf(slots[i]) ?: continue
            val apps = f.apps.filter { it in appsByKey }
            if (apps.size <= 1) {
                slots[i] = apps.firstOrNull() ?: EMPTY_SLOT
                map = map - f.id
            }
        }
        // Drop folders that are no longer on the grid at all.
        val onGrid = slots.filter { it.startsWith(FOLDER_PREFIX) }.map { it.removePrefix(FOLDER_PREFIX) }.toSet()
        map = map.filterKeys { it in onGrid }
        if (map != folders) saveFolders(map)
        return slots
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

    // ---------- Launch animation & home-screen motion ----------

    /** Supplies the window's root view (set by MainActivity) for "grow out of the icon" launches. */
    var hostView: (() -> android.view.View?)? = null
    /** Where each app icon currently sits on screen, recorded as the icons are laid out. */
    val iconBounds = HashMap<String, android.graphics.Rect>()
    /** Bumped on every app launch / every return to home, to drive the zoom animations. */
    var launchTick by mutableStateOf(0); private set
    var resumeTick by mutableStateOf(0); private set
    var resumed = false; private set

    fun onPause() { resumed = false }

    fun launch(app: AppEntry) {
        val bounds = iconBounds[app.key]
        val view = hostView?.invoke()
        val options = if (prefs.openAnimation && bounds != null && view != null) {
            // The app grows out of its icon.
            android.app.ActivityOptions.makeScaleUpAnimation(
                view, bounds.left, bounds.top, bounds.width(), bounds.height(),
            ).toBundle()
        } else null
        repo.launch(app, bounds, options)
        launchTick++
        // Remember for the "Recent" row (only launches through Panel; no usage-access permission).
        prefs.recentApps = (listOf(app.key) + prefs.recentApps.filter { it != app.key }).take(12)
    }

    /** Pulls down the notification shade, like swiping down on the Pixel launcher. */
    @android.annotation.SuppressLint("WrongConstant")
    fun expandNotifications(): Boolean = runCatching {
        val sbm = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
    }.isSuccess

    fun iconPacks(): List<com.panel.launcher.data.IconPacks.Pack> =
        com.panel.launcher.data.IconPacks.installed(ctx)

    fun setIconStyle(style: String, pack: String = prefs.iconPack) {
        prefs.iconStyle = style
        prefs.iconPack = pack
        refreshApps()
    }

    fun recentApps(n: Int): List<AppEntry> = prefs.recentApps.mapNotNull { appsByKey[it] }.take(n)

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
        media.stop()
        runCatching { android.app.WallpaperManager.getInstance(ctx).removeOnColorsChangedListener(wallpaperColorsListener) }
        repo.unwatch(appsCallback)
        if (observingCalendar) ctx.contentResolver.unregisterContentObserver(calendarObserver)
    }
}
