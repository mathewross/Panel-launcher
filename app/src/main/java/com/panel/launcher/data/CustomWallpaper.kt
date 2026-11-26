package com.panel.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * A wallpaper photo that Panel draws itself, so it can put the big clock *between* the photo
 * and its subject (the iOS "depth effect"). The subject is cut out on-device with ML Kit.
 */
object CustomWallpaper {
    private fun wallFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")
    private fun fgFile(ctx: Context) = File(ctx.filesDir, "wallpaper_subject.png")

    suspend fun load(ctx: Context): Pair<Bitmap?, Bitmap?> = withContext(Dispatchers.IO) {
        val w = wallFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        val f = if (w != null) fgFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } else null
        w to f
    }

    /** Copies the picked photo into app storage, scaled to a sensible size. */
    suspend fun import(ctx: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val maxSide = 2400
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = maxSide.toFloat() / max(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        }
        wallFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        fgFile(ctx).delete()
        bmp
    }

    /** Cuts the main subject out of [bmp]; returns a same-size bitmap with a transparent background. */
    suspend fun segment(ctx: Context, bmp: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        // Segment a smaller copy (faster), then scale the cut-out back up to the photo's size.
        val scale = 1280f / max(bmp.width, bmp.height)
        val input = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else bmp
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
        try {
            // The model is a Google Play services module; the first use may need to wait for its download.
            repeat(10) {
                val result = runCatching { segmenter.process(InputImage.fromBitmap(input, 0)).await() }
                val fg = result.getOrNull()?.foregroundBitmap
                if (fg != null) {
                    val full = if (fg.width != bmp.width || fg.height != bmp.height) {
                        Bitmap.createScaledBitmap(fg, bmp.width, bmp.height, true)
                    } else fg
                    withContext(Dispatchers.IO) {
                        fgFile(ctx).outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    return@withContext full
                }
                if (result.isSuccess) return@withContext null // ran fine, but found no subject
                delay(4000)
            }
            null
        } finally {
            segmenter.close()
        }
    }

    fun clear(ctx: Context) {
        wallFile(ctx).delete()
        fgFile(ctx).delete()
    }

    /** Optionally also use the photo as the phone's own wallpaper (home + lock screen). */
    suspend fun setAsSystemWallpaper(ctx: Context, bmp: Bitmap) = withContext(Dispatchers.IO) {
        WallpaperManager.getInstance(ctx).setBitmap(
            bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
        )
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }
}
