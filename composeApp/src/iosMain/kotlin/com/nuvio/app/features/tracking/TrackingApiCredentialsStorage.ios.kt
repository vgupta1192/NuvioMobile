package com.nuvio.app.features.tracking

import platform.Foundation.NSUserDefaults

internal actual object TrackingApiCredentialsStorage {
    private const val traktClientIdKey = "tracking_api_trakt_client_id"
    private const val traktClientSecretKey = "tracking_api_trakt_client_secret"
    private const val simklClientIdKey = "tracking_api_simkl_client_id"

    actual fun loadTraktClientId(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(traktClientIdKey)

    actual fun saveTraktClientId(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = traktClientIdKey)
    }

    actual fun loadTraktClientSecret(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(traktClientSecretKey)

    actual fun saveTraktClientSecret(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = traktClientSecretKey)
    }

    actual fun loadSimklClientId(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(simklClientIdKey)

    actual fun saveSimklClientId(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = simklClientIdKey)
    }
}
