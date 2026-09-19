package com.nuvio.app.features.tracking

import android.content.Context
import android.content.SharedPreferences

internal actual object TrackingApiCredentialsStorage {
    private const val preferencesName = "nuvio_tracking_api_credentials"
    private const val traktClientIdKey = "trakt_client_id"
    private const val traktClientSecretKey = "trakt_client_secret"
    private const val simklClientIdKey = "simkl_client_id"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadTraktClientId(): String? =
        preferences?.getString(traktClientIdKey, null)

    actual fun saveTraktClientId(value: String) {
        preferences?.edit()?.putString(traktClientIdKey, value)?.apply()
    }

    actual fun loadTraktClientSecret(): String? =
        preferences?.getString(traktClientSecretKey, null)

    actual fun saveTraktClientSecret(value: String) {
        preferences?.edit()?.putString(traktClientSecretKey, value)?.apply()
    }

    actual fun loadSimklClientId(): String? =
        preferences?.getString(simklClientIdKey, null)

    actual fun saveSimklClientId(value: String) {
        preferences?.edit()?.putString(simklClientIdKey, value)?.apply()
    }
}
