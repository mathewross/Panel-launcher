package com.panel.launcher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.panel.launcher.MediaListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MediaAction(val id: String, val name: String, val icon: ImageBitmap?)

data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    /** App-specific buttons such as shuffle, repeat or like (full mode only). */
    val actions: List<MediaAction>,
    /** True when Panel can only send play/pause/next/previous (Spotify broadcast mode). */
    val basicOnly: Boolean,
)

/**
 * What's playing, from one of two sources:
 *  - Media sessions, *only if* the user has granted Panel notification access (any music app,
 *    with shuffle/like/etc.).
 *  - Otherwise Spotify's "Device broadcast status" broadcasts — no permission at all. Controls
 *    are sent as normal media-button presses.
 */
class MediaRepository(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onChange: (NowPlaying?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val sessions = ctx.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(ctx, MediaListener::class.java)

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    // ------------------------------------------------------------------ lifecycle

    private var started = false
    private var usingSessions = false

    fun start() {
        if (!started) {
            started = true
            val filter = IntentFilter().apply {
                addAction(SPOTIFY_METADATA)
                addAction(SPOTIFY_PLAYBACK)
            }
            ContextCompat.registerReceiver(ctx, spotifyReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        refreshMode()
    }

    /** Call on resume: picks up notification access being granted or revoked. */
    fun refreshMode() {
        val access = hasNotificationAccess()
        if (access && !usingSessions) {
            runCatching {
                sessions.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
                usingSessions = true
                pickController(sessions.getActiveSessions(listenerComponent))
            }
        } else if (!access && usingSessions) {
            runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsListener) }
            usingSessions = false
            controller?.unregisterCallback(controllerCallback)
            controller = null
            publishSpotify()
        } else if (usingSessions) {
            runCatching { pickController(sessions.getActiveSessions(listenerComponent)) }
        }
    }

    fun stop() {
        if (started) runCatching { ctx.unregisterReceiver(spotifyReceiver) }
        if (usingSessions) runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        started = false
        usingSessions = false
    }

    // ------------------------------------------------------------------ controls

    fun playPause(current: NowPlaying) {
        val c = controller
        if (c != null) {
            if (current.playing) c.transportControls.pause() else c.transportControls.play()
        } else {
            sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            spPlaying = !current.playing // optimistic; Spotify will confirm
            publishSpotify()
        }
    }

    fun next() {
        controller?.transportControls?.skipToNext() ?: sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious() ?: sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun custom(action: MediaAction) {
        controller?.transportControls?.sendCustomAction(action.id, null)
    }

    private fun sendKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ------------------------------------------------------------------ media sessions (opt-in)

    private var controller: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pickController(list ?: emptyList())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishController()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Another app may have started playing; re-pick the most relevant session.
            runCatching { pickController(sessions.getActiveSessions(listenerComponent)) }
        }
        override fun onSessionDestroyed() {
            runCatching { pickController(sessions.getActiveSessions(listenerComponent)) }
        }
    }

    private fun isPlaying(c: MediaController) = c.playbackState?.state.let {
        it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING
    }

    private fun pickController(list: List<MediaController>) {
        val best = list.firstOrNull { isPlaying(it) } ?: list.firstOrNull { it.metadata != null }
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, main)
        }
        publishController()
    }

    private fun publishController() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) { onChange(null); return }
        val art = (md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.asImageBitmap()
        val res = runCatching { ctx.packageManager.getResourcesForApplication(c.packageName) }.getOrNull()
        val actions = c.playbackState?.customActions.orEmpty().map { a ->
            val icon = runCatching {
                res?.getDrawable(a.icon, null)?.toBitmap(64, 64)?.asImageBitmap()
            }.getOrNull()
            MediaAction(a.action, a.name.toString(), icon)
        }
        onChange(
            NowPlaying(
                packageName = c.packageName,
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
                art = art,
                playing = isPlaying(c),
                actions = actions,
                basicOnly = false,
            )
        )
    }

    // ------------------------------------------------------------------ Spotify broadcasts (no permission)

    private var spTrackId: String? = null
    private var spTitle: String? = null
    private var spArtist: String? = null
    private var spPlaying = false
    private var spArt: ImageBitmap? = null

    private val spotifyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                SPOTIFY_METADATA -> {
                    spTitle = i.getStringExtra("track")
                    spArtist = i.getStringExtra("artist")
                    val id = i.getStringExtra("id")
                    if (id != null && id != spTrackId) {
                        spTrackId = id
                        spArt = null
                        fetchSpotifyArt(id)
                    }
                }
                SPOTIFY_PLAYBACK -> spPlaying = i.getBooleanExtra("playing", false)
            }
            if (!usingSessions) publishSpotify()
        }
    }

    private fun publishSpotify() {
        val title = spTitle
        if (title == null) { onChange(null); return }
        onChange(NowPlaying(SPOTIFY_PACKAGE, title, spArtist.orEmpty(), spArt, spPlaying, emptyList(), basicOnly = true))
    }

    /** Album art from Spotify's public oEmbed endpoint (no account or key needed). */
    private fun fetchSpotifyArt(trackUri: String) {
        val trackId = trackUri.substringAfterLast(':')
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val meta = get("https://open.spotify.com/oembed?url=https://open.spotify.com/track/$trackId")
                    val thumb = JSONObject(String(meta)).getString("thumbnail_url")
                    val bytes = get(thumb)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            if (spTrackId == trackUri && bmp != null) {
                spArt = bmp.asImageBitmap()
                if (!usingSessions) publishSpotify()
            }
        }
    }

    private fun get(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try { conn.inputStream.use { it.readBytes() } } finally { conn.disconnect() }
    }

    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val SPOTIFY_METADATA = "com.spotify.music.metadatachanged"
        private const val SPOTIFY_PLAYBACK = "com.spotify.music.playbackstatechanged"
    }
}
