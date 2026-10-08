package com.nuvio.app.features.jellyfin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Minimal Jellyfin REST client over [JellyfinPlatform]. Jellyfin 12 killed the legacy X-Emby-*
 * headers, so every call uses `Authorization: MediaBrowser …` (token appended once signed in) and
 * media/image URLs carry the token as the `api_key` query parameter so plain URL consumers
 * (poster loading, the player) need no headers.
 */
internal object JellyfinClient {
    private const val PAGE_SIZE = 60
    private const val CLIENT_NAME = "Nuvio Jellyfin"
    private const val DEVICE_NAME = "Nuvio Desktop"
    private const val APP_VERSION = "0.1"
    private const val DEVICE_ID_KEY = "device_id"

    private val json = Json { ignoreUnknownKeys = true }

    fun normalizeServerUrl(raw: String): String? {
        var url = raw.trim()
        if (url.isBlank()) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        url = url.trimEnd('/')
        val host = url.removePrefix("https://").removePrefix("http://")
        return url.takeIf { host.isNotBlank() }
    }

    private fun deviceId(): String {
        JellyfinPlatform.loadString(DEVICE_ID_KEY)?.let { existing ->
            if (existing.isNotBlank()) return existing
        }
        val generated = "nuvio-" + buildString {
            repeat(8) { append(kotlin.random.Random.nextInt(16).toString(16)) }
        }
        JellyfinPlatform.saveString(DEVICE_ID_KEY, generated)
        return generated
    }

    private fun authHeaders(token: String?, contentType: String? = null): Map<String, String> {
        val authorization = buildString {
            append("MediaBrowser Client=\"").append(CLIENT_NAME)
                .append("\", Device=\"").append(DEVICE_NAME)
                .append("\", DeviceId=\"").append(deviceId())
                .append("\", Version=\"").append(APP_VERSION).append("\"")
            if (!token.isNullOrBlank()) append(", Token=\"").append(token).append("\"")
        }
        return buildMap {
            put("Authorization", authorization)
            if (contentType != null) put("Content-Type", contentType)
        }
    }

    suspend fun authenticate(serverUrl: String, username: String, password: String): JellyfinSession {
        val base = normalizeServerUrl(serverUrl) ?: error("Enter a valid Jellyfin server address")
        if (username.isBlank()) error("Enter your Jellyfin username")
        val response = JellyfinPlatform.httpCall(
            method = "POST",
            url = "$base/Users/AuthenticateByName",
            headers = authHeaders(token = null, contentType = "application/json"),
            body = buildJsonObject {
                put("Username", username)
                put("Pw", password)
            }.toString(),
        ) ?: error("Could not reach the Jellyfin server")
        if (response.status == 401) error("Wrong username or password")
        if (response.status !in 200..299) error("Jellyfin server error ${response.status}")
        val root = runCatching { response.body.parseJson(json) }.getOrNull()
            ?: error("Unexpected response from the Jellyfin server")
        val token = root.string("AccessToken") ?: error("Jellyfin did not return an access token")
        val user = root["User"] as? JsonObject
        val userId = user?.string("Id") ?: error("Jellyfin did not return a user id")
        val userName = user.string("Name") ?: username
        return JellyfinSession(
            serverUrl = base,
            serverName = fetchServerName(base),
            userId = userId,
            userName = userName,
            accessToken = token,
        )
    }

    private suspend fun fetchServerName(base: String): String = runCatching {
        val response = JellyfinPlatform.httpCall(
            method = "GET",
            url = "$base/System/Info/Public",
            headers = emptyMap(),
            body = null,
        )
        response
            ?.takeIf { it.status in 200..299 }
            ?.let { it.body.parseJson(json).string("ServerName") }
    }.getOrNull() ?: "Jellyfin"

    private suspend fun call(session: JellyfinSession, path: String, query: Map<String, String?> = emptyMap()): JsonObject {
        val url = buildString {
            append(session.serverUrl).append(path)
            val params = query.filterValues { !it.isNullOrBlank() }
            if (params.isNotEmpty()) {
                append('?')
                params.entries.forEachIndexed { index, (name, value) ->
                    if (index > 0) append('&')
                    append(name).append('=').append(encodeQueryValue(value.orEmpty()))
                }
            }
        }
        val response = JellyfinPlatform.httpCall(
            method = "GET",
            url = url,
            headers = authHeaders(session.accessToken),
            body = null,
        ) ?: error("Could not reach the Jellyfin server")
        if (response.status == 401) error("Jellyfin session expired — sign in again")
        if (response.status !in 200..299) error("Jellyfin server error ${response.status}")
        return runCatching { response.body.parseJson(json) }.getOrNull()
            ?: error("Unexpected response from the Jellyfin server")
    }

    suspend fun getLibraries(session: JellyfinSession): List<JellyfinLibrary> {
        val root = call(session, "/Users/${session.userId}/Views")
        return root.array("Items").orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("Id") ?: return@mapNotNull null
            val name = obj.string("Name") ?: return@mapNotNull null
            JellyfinLibrary(
                id = id,
                name = name,
                collectionType = obj.string("CollectionType"),
            )
        }
    }

    suspend fun getItem(session: JellyfinSession, itemId: String): JellyfinItem? {
        val root = call(session, "/Users/${session.userId}/Items/$itemId")
        return parseItem(root)
    }

    suspend fun getItems(
        session: JellyfinSession,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = PAGE_SIZE,
        sortBy: String = "SortName",
        sortAscending: Boolean = true,
        searchTerm: String? = null,
        includeItemTypes: String? = null,
        recursive: Boolean = false,
    ): JellyfinItemPage {
        val root = call(
            session,
            "/Users/${session.userId}/Items",
            buildMap {
                put("SortBy", sortBy)
                put("SortOrder", if (sortAscending) "Ascending" else "Descending")
                put("StartIndex", startIndex.toString())
                put("Limit", limit.toString())
                put(
                    "Fields",
                    "Overview,Genres,ProductionYear,CommunityRating,OfficialRating,RunTimeTicks," +
                        "Container,UserData,SeriesId,SeriesName",
                )
                put("Recursive", recursive.toString())
                put("ImageTypeLimit", "1")
                put("EnableImageTypes", "Primary,Backdrop,Thumb")
                if (parentId != null) put("ParentId", parentId)
                if (searchTerm != null) put("SearchTerm", searchTerm)
                if (includeItemTypes != null) put("IncludeItemTypes", includeItemTypes)
            },
        )
        val items = root.array("Items").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseItem)
        }
        return JellyfinItemPage(
            items = items,
            totalRecordCount = root.int("TotalRecordCount") ?: items.size,
        )
    }

    suspend fun getSeasons(session: JellyfinSession, seriesId: String): List<JellyfinItem> {
        val root = call(
            session,
            "/Shows/$seriesId/Seasons",
            mapOf("userId" to session.userId),
        )
        return root.array("Items").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseItem)
        }
    }

    suspend fun getEpisodes(session: JellyfinSession, seriesId: String, seasonId: String): List<JellyfinItem> {
        val root = call(
            session,
            "/Shows/$seriesId/Episodes",
            mapOf(
                "userId" to session.userId,
                "seasonId" to seasonId,
                "Fields" to "Overview,CommunityRating,RunTimeTicks,Container,UserData,SeriesId,SeriesName",
                "ImageTypeLimit" to "1",
                "EnableImageTypes" to "Primary,Thumb",
            ),
        )
        return root.array("Items").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseItem)
        }
    }

    fun primaryImageUrl(session: JellyfinSession, item: JellyfinItem, maxWidth: Int = 480): String? {
        if (item.imageTag.isNullOrBlank()) return null
        return "${session.serverUrl}/Items/${item.id}/Images/Primary" +
            "?maxWidth=$maxWidth&quality=88&api_key=${encodeQueryValue(session.accessToken)}"
    }

    fun backdropImageUrl(session: JellyfinSession, item: JellyfinItem, maxWidth: Int = 1280): String? {
        if (item.backdropTag.isNullOrBlank()) return null
        return "${session.serverUrl}/Items/${item.id}/Images/Backdrop/0" +
            "?maxWidth=$maxWidth&quality=80&api_key=${encodeQueryValue(session.accessToken)}"
    }

    /** Direct-play URL for the item's original file (no transcode). */
    fun streamUrl(session: JellyfinSession, item: JellyfinItem): String =
        "${session.serverUrl}/Videos/${item.id}/stream" +
            "?Static=true&api_key=${encodeQueryValue(session.accessToken)}"

    private fun parseItem(obj: JsonObject): JellyfinItem? {
        val id = obj.string("Id") ?: return null
        val name = obj.string("Name") ?: return null
        val userData = obj["UserData"] as? JsonObject
        return JellyfinItem(
            id = id,
            name = name,
            type = obj.string("Type") ?: "",
            overview = obj.string("Overview")?.takeIf { it.isNotBlank() },
            productionYear = obj.int("ProductionYear"),
            communityRating = obj.double("CommunityRating"),
            officialRating = obj.string("OfficialRating"),
            runTimeTicks = obj.long("RunTimeTicks"),
            indexNumber = obj.int("IndexNumber"),
            parentIndexNumber = obj.int("ParentIndexNumber"),
            seriesId = obj.string("SeriesId"),
            seriesName = obj.string("SeriesName"),
            imageTag = (obj["ImageTags"] as? JsonObject)?.string("Primary"),
            backdropTag = (obj["BackdropImageTags"] as? JsonArray)
                ?.firstOrNull()
                ?.jsonPrimitive
                ?.contentOrNull,
            container = obj.string("Container"),
            playbackPositionTicks = userData?.long("PlaybackPositionTicks"),
            playedPercentage = userData?.double("PlayedPercentage"),
        )
    }

    private fun String.parseJson(json: Json): JsonObject =
        json.parseToJsonElement(this).jsonObject

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.double(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.array(key: String): JsonArray? =
        this[key] as? JsonArray

    /** RFC 3986 query-value encoding without java.net (common code). */
    private fun encodeQueryValue(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            when {
                code in 48..57 || code in 65..90 || code in 97..122 -> append(code.toChar())
                code == '-'.code || code == '_'.code || code == '.'.code || code == '~'.code -> append(code.toChar())
                else -> {
                    append('%')
                    append(code.toString(16).padStart(2, '0'))
                }
            }
        }
    }
}
