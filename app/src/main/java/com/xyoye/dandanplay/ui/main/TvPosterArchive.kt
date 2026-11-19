package com.xyoye.dandanplay.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Small, durable poster cache for the TV anime grid.
 *
 * Posters are recompressed at the requested TV size before they are stored. Entries use a
 * rolling 90-day retention window and the archive has a hard size limit so old devices do not
 * accumulate an unbounded image cache.
 */
internal class TvPosterArchive(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = RETENTION_MILLIS,
    private val maxBytes: Long = MAX_BYTES
) {
    private val lock = Any()
    private val writesInFlight = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "tv-poster-archive").apply { isDaemon = true }
    }
    @Volatile
    private var lastCleanupAt = now()

    fun fresh(url: String): File? = cachedFile(url)?.takeIf { file ->
        val age = now() - file.lastModified()
        age <= maxAgeMillis
    }

    fun stale(url: String): File? = cachedFile(url)?.takeIf { it.length() > 0 }

    fun discard(url: String) {
        synchronized(lock) {
            cachedFile(url)?.delete()
        }
    }

    fun storeAsync(url: String, drawable: Drawable) {
        if (url.isBlank() || drawable !is BitmapDrawable || !writesInFlight.add(url)) return
        val bitmap = drawable.bitmap ?: run {
            writesInFlight.remove(url)
            return
        }
        writer.execute {
            try {
                val output = ByteArrayOutputStream()
                if (bitmap.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, output)) {
                    write(url, output.toByteArray())
                }
            } finally {
                writesInFlight.remove(url)
            }
        }
    }

    internal fun write(url: String, bytes: ByteArray) {
        if (url.isBlank() || bytes.isEmpty()) return
        synchronized(lock) {
            directory.mkdirs()
            val target = fileFor(url)
            val pending = File.createTempFile("poster-", ".pending", directory)
            try {
                pending.writeBytes(bytes)
                if (!pending.renameTo(target)) {
                    if (target.exists()) target.delete()
                    pending.copyTo(target, overwrite = true)
                }
                target.setLastModified(now())
            } finally {
                pending.delete()
            }
            pruneLocked()
        }
    }

    fun cleanupIfNeeded(force: Boolean = false) {
        val current = now()
        if (!force && current - lastCleanupAt < CLEANUP_INTERVAL_MILLIS) return
        synchronized(lock) {
            if (!force && current - lastCleanupAt < CLEANUP_INTERVAL_MILLIS) return
            lastCleanupAt = current
            directory.mkdirs()
            pruneLocked()
        }
    }

    private fun cachedFile(url: String): File? {
        if (url.isBlank()) return null
        val file = fileFor(url)
        return file.takeIf { it.isFile }
    }

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(directory, "$digest.webp")
    }

    private fun pruneLocked() {
        val entries = directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".webp") }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        val current = now()
        var retainedBytes = 0L
        val retained = mutableListOf<File>()

        entries.forEach { file ->
            val age = current - file.lastModified()
            if (age > maxAgeMillis || file.length() <= 0) {
                file.delete()
            } else {
                retained.add(file)
                retainedBytes += file.length()
            }
        }

        retained.forEach { file ->
            if (retainedBytes <= maxBytes) return@forEach
            val size = file.length()
            if (file.delete()) retainedBytes -= size
        }
    }

    companion object {
        private const val RETENTION_DAYS = 90L
        private const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60L * 60L * 1000L
        private const val CLEANUP_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
        private const val MAX_BYTES = 64L * 1024L * 1024L
        private const val WEBP_QUALITY = 82

        @Volatile
        private var instance: TvPosterArchive? = null

        fun shared(context: Context): TvPosterArchive =
            instance ?: synchronized(this) {
                instance ?: TvPosterArchive(File(context.applicationContext.filesDir, "ani-poster-cache-v1"))
                    .also { instance = it }
            }
    }
}
