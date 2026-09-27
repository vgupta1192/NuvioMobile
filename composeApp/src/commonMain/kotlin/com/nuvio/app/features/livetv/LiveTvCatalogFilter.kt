package com.nuvio.app.features.livetv

import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.ManagedAddon

/**
 * Decides which addon catalogs are live TV. Live TV catalogs appear only on the Live TV screen;
 * Home, Search/Discover and Library leave them out.
 */
object LiveTvCatalogFilter {
    private val tvCatalogTypes = setOf(
        "tv", "channel", "channels", "iptv", "live", "livetv", "live_tv", "broadcast", "radio",
        "sports", "sport", "events", "event", "news",
    )
    private val vodTypes = setOf("movie", "series", "anime")

    /** Library and other saved items: true when the content type is a live TV type. */
    fun isLiveTvType(type: String): Boolean = type.lowercase().trim() in tvCatalogTypes

    /**
     * A catalog is live TV when its type or name says so, or when it belongs to an addon that
     * looks like a TV addon and is not a movie/series/anime catalog.
     */
    fun isLiveTvCatalog(addon: ManagedAddon, catalog: AddonCatalog): Boolean {
        if (isTvCatalog(catalog)) return true
        if (catalog.type.lowercase().trim() in vodTypes) return false
        return looksLikeTvAddon(addon)
    }

    fun isTvCatalog(catalog: AddonCatalog): Boolean {
        val type = catalog.type.lowercase().trim()
        if (type in tvCatalogTypes) return true
        if (type in vodTypes) return false
        val text = "${catalog.id} ${catalog.name}".lowercase()
        return listOf("channel", "iptv", "live", "canal", "canais", "ao vivo").any { text.contains(it) }
    }

    fun looksLikeTvAddon(addon: ManagedAddon): Boolean {
        val manifest = addon.manifest ?: return false
        if (manifest.types.any { it.lowercase() in tvCatalogTypes }) return true
        val text = "${manifest.id} ${manifest.name} ${manifest.description}".lowercase()
        if (listOf("iptv", "live tv", "livetv", "tv channels", "channels", "canais", "ao vivo").any { text.contains(it) }) return true
        return manifest.catalogs.any { isTvCatalog(it) }
    }
}
