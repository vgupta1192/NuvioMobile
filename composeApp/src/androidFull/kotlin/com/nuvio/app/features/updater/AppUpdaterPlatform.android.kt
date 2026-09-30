package com.nuvio.app.features.updater

actual object AppUpdaterPlatform {
    actual val isSupported: Boolean = true
    // Self-host fork patch: the fork ships debug-type APKs as its normal build; treat them as
    // release so the update check runs at start (upstream skips it for debug builds)
    actual val isDebugBuild: Boolean
        get() = false

    actual fun getSupportedAbis(): List<String> = AndroidAppUpdaterPlatform.getSupportedAbis()

    actual fun getIgnoredTag(): String? = AndroidAppUpdaterPlatform.getIgnoredTag()

    actual fun setIgnoredTag(tag: String?) {
        AndroidAppUpdaterPlatform.setIgnoredTag(tag)
    }

    actual fun getUpdateChannel(): String? = AndroidAppUpdaterPlatform.getUpdateChannel()

    actual fun setUpdateChannel(channel: String) {
        AndroidAppUpdaterPlatform.setUpdateChannel(channel)
    }

    actual fun deleteDownloadedApk(path: String) {
        AndroidAppUpdaterPlatform.deleteDownloadedApk(path)
    }

    actual suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = AndroidAppUpdaterPlatform.downloadApk(assetUrl, assetName, onProgress)

    actual fun canRequestPackageInstalls(): Boolean = AndroidAppUpdaterPlatform.canRequestPackageInstalls()

    actual fun openUnknownSourcesSettings() {
        AndroidAppUpdaterPlatform.openUnknownSourcesSettings()
    }

    actual fun installDownloadedApk(path: String): Result<Unit> = AndroidAppUpdaterPlatform.installDownloadedApk(path)
}
