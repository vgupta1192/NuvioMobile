package com.nuvio.app.features.livetv

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

internal actual object LiveTvPlatform {
    actual val navEntryEnabled: Boolean = true

    private var cacheRoot: File? = null

    fun initialize(context: Context) {
        cacheRoot = context.cacheDir
        LiveTvStorage.initialize(context)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    actual suspend fun fetchGuideText(cacheKey: String, url: String, forceRefresh: Boolean, maxAgeMs: Long): String? =
        withContext(Dispatchers.IO) {
            val dir = cacheRoot?.resolve("livetv-epg")?.also { it.mkdirs() }
            val safeKey = cacheKey.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val cacheFile = dir?.resolve("$safeKey.xml")
            val cached = cacheFile?.takeIf { it.isFile }
            if (!forceRefresh && cached != null) {
                val age = System.currentTimeMillis() - cached.lastModified()
                if (age in 0 until maxAgeMs) {
                    return@withContext runCatching { cached.readText() }.getOrNull()
                }
            }
            val downloaded = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NuvioMobile-LiveTV")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    val input = BufferedInputStream(body.byteStream(), 64 * 1024)
                    input.mark(2)
                    val b1 = input.read()
                    val b2 = input.read()
                    input.reset()
                    val stream: InputStream = if (b1 == 0x1F && b2 == 0x8B) GZIPInputStream(input, 64 * 1024) else input
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (downloaded != null) {
                if (dir != null && cacheFile != null) {
                    runCatching {
                        val temp = dir.resolve("$safeKey.tmp")
                        temp.writeText(downloaded)
                        if (!temp.renameTo(cacheFile)) {
                            temp.copyTo(cacheFile, overwrite = true)
                            temp.delete()
                        }
                    }
                }
                return@withContext downloaded
            }
            // Offline or source down: an old guide still beats none.
            cached?.let { runCatching { it.readText() }.getOrNull() }
        }

    actual fun formatClock(epochMs: Long): String =
        runCatching { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs)) }
            .getOrDefault("--:--")
}

internal actual object LiveTvStorage {
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("nuvio_livetv", Context.MODE_PRIVATE)
    }

    actual fun loadString(key: String): String? = preferences?.getString(key, null)

    actual fun saveString(key: String, value: String?) {
        preferences?.edit()?.apply {
            if (value == null) remove(key) else putString(key, value)
        }?.apply()
    }
}
