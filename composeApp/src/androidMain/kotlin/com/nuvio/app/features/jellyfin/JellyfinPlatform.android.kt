package com.nuvio.app.features.jellyfin

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal actual object JellyfinPlatform {
    actual val navEntryEnabled: Boolean = true

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("nuvio_jellyfin", Context.MODE_PRIVATE)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    actual suspend fun httpCall(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): JellyfinHttpResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url)
            headers.forEach { (name, value) -> builder.header(name, value) }
            when (method.uppercase()) {
                "POST" -> builder.post((body.orEmpty()).toRequestBody("application/json".toMediaType()))
                "GET" -> builder.get()
                else -> builder.method(method.uppercase(), null)
            }
            client.newCall(builder.build()).execute().use { response ->
                JellyfinHttpResponse(
                    status = response.code,
                    body = response.body?.string().orEmpty(),
                )
            }
        }.getOrNull()
    }

    actual fun loadString(key: String): String? = preferences?.getString(key, null)

    actual fun saveString(key: String, value: String?) {
        preferences?.edit()?.apply {
            if (value == null) remove(key) else putString(key, value)
        }?.apply()
    }
}
