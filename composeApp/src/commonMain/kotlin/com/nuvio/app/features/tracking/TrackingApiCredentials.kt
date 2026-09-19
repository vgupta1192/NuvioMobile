package com.nuvio.app.features.tracking

import com.nuvio.app.features.simkl.SimklConfig
import com.nuvio.app.features.trakt.TraktConfig

/**
 * Platform persistence for user-supplied tracking API credentials (self-hosted builds
 * where the client IDs/secret are not baked in at build time).
 */
internal expect object TrackingApiCredentialsStorage {
    fun loadTraktClientId(): String?
    fun saveTraktClientId(value: String)
    fun loadTraktClientSecret(): String?
    fun saveTraktClientSecret(value: String)
    fun loadSimklClientId(): String?
    fun saveSimklClientId(value: String)
}

/**
 * Applies stored credential overrides to the generated TraktConfig/SimklConfig objects
 * and exposes save/inspect accessors for the settings screen.
 */
object TrackingApiCredentials {
    fun applyStoredOverrides() {
        TrackingApiCredentialsStorage.loadTraktClientId()
            ?.takeIf { it.isNotBlank() }
            ?.let { TraktConfig.CLIENT_ID = it }
        TrackingApiCredentialsStorage.loadTraktClientSecret()
            ?.takeIf { it.isNotBlank() }
            ?.let { TraktConfig.CLIENT_SECRET = it }
        TrackingApiCredentialsStorage.loadSimklClientId()
            ?.takeIf { it.isNotBlank() }
            ?.let { SimklConfig.CLIENT_ID = it }
    }

    fun effectiveTraktClientId(): String = TraktConfig.CLIENT_ID
    fun effectiveTraktClientSecret(): String = TraktConfig.CLIENT_SECRET
    fun effectiveSimklClientId(): String = SimklConfig.CLIENT_ID

    fun saveTraktCredentials(clientId: String, clientSecret: String) {
        val id = clientId.trim()
        val secret = clientSecret.trim()
        TraktConfig.CLIENT_ID = id
        TraktConfig.CLIENT_SECRET = secret
        TrackingApiCredentialsStorage.saveTraktClientId(id)
        TrackingApiCredentialsStorage.saveTraktClientSecret(secret)
    }

    fun saveSimklClientId(clientId: String) {
        val id = clientId.trim()
        SimklConfig.CLIENT_ID = id
        TrackingApiCredentialsStorage.saveSimklClientId(id)
    }
}
