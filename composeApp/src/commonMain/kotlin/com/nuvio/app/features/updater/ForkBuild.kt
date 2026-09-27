package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionConfig

/**
 * Self-host fork patch: updates come from github.com/vgupta1192/NuvioMobile releases. CI builds
 * every APK with versionName "<upstream version>-fork.<CI run number>" and names the asset
 * NuvioMobile-<version>-b<run number>.apk, so "newer" = higher run number (fork rebuilds of the
 * same upstream version keep the same version name otherwise).
 */
internal object ForkBuild {
    const val REPO = "vgupta1192/NuvioMobile"
    private const val TAG_PREFIX = "fork-build-"
    private val assetPattern = Regex("-b(\\d+)\\.apk$", RegexOption.IGNORE_CASE)

    val localBuild: Int
        get() = AppVersionConfig.VERSION_NAME.substringAfter("-fork.", "").toIntOrNull() ?: 0

    fun tag(build: Int): String = "$TAG_PREFIX$build"

    fun isNewer(tag: String): Boolean {
        val remote = tag.removePrefix(TAG_PREFIX).toIntOrNull() ?: return false
        return remote > localBuild
    }

    fun newest(releases: List<GitHubReleaseDto>): AppUpdate? {
        var best: AppUpdate? = null
        var bestBuild = -1
        for (release in releases) {
            if (release.draft) continue
            for (asset in release.assets) {
                val build = assetPattern.find(asset.name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (build <= bestBuild) continue
                bestBuild = build
                val name = release.name?.takeIf { it.isNotBlank() } ?: release.tagName.orEmpty()
                best = AppUpdate(
                    tag = tag(build),
                    title = "$name (build $build)".trim(),
                    notes = release.body.orEmpty(),
                    releaseUrl = release.htmlUrl,
                    assetName = asset.name,
                    assetUrl = asset.browserDownloadUrl,
                    assetSizeBytes = asset.size,
                )
            }
        }
        return best
    }
}
