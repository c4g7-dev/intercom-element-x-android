/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

/** A release picked from the list, before its `android-update.json` or `SHA256SUMS` has been read. */
data class SelectedRelease(
    val version: InterComVersion,
    val release: GitHubRelease,
    val apk: GitHubAsset,
    val manifest: GitHubAsset?,
    val sha256Sums: GitHubAsset?,
)

object ReleaseSelector {
    const val MANIFEST_NAME = "android-update.json"
    const val SHA256SUMS_NAME = "SHA256SUMS"
    const val ABI = "arm64-v8a"

    /** Every file of a release is downloaded from below this, and from nowhere else. */
    const val DOWNLOAD_PREFIX = "https://github.com/c4g7-dev/InterCom/releases/download/"

    /**
     * The newest release of InterCom for Android that is newer than [currentVersionCode], or null.
     *
     * Only tags `android-v<version>` count, which leaves out the desktop's releases (`v<version>`) of the same
     * repository. Drafts never count, pre-releases only with [includePrereleases]. A release must carry the
     * arm64-v8a APK and something to check it against.
     */
    fun select(
        releases: List<GitHubRelease>,
        currentVersionCode: Long,
        includePrereleases: Boolean,
    ): SelectedRelease? {
        return releases
            .asSequence()
            .filter { !it.draft && (includePrereleases || !it.prerelease) }
            .mapNotNull { release -> InterComVersion.parseTag(release.tagName)?.let { toSelected(it, release) } }
            .filter { it.version.versionCode() > currentVersionCode }
            .maxByOrNull { it.version }
    }

    private fun toSelected(version: InterComVersion, release: GitHubRelease): SelectedRelease? {
        val assets = release.assets.filter { it.downloadUrl.startsWith(DOWNLOAD_PREFIX) }
        val apk = assets.firstOrNull { it.name == apkName(version) } ?: return null
        val manifest = assets.firstOrNull { it.name == MANIFEST_NAME }
        val sums = assets.firstOrNull { it.name == SHA256SUMS_NAME }
        if (manifest == null && sums == null) return null
        return SelectedRelease(version, release, apk, manifest, sums)
    }

    /**
     * The notes of the Android releases above [afterVersionCode] and up to [upToVersionCode], newest first: what an
     * update from the one to the other brings. Releases without notes are left out, and at most [MAX_NOTES] are kept.
     */
    fun notes(
        releases: List<GitHubRelease>,
        afterVersionCode: Long,
        upToVersionCode: Long,
        includePrereleases: Boolean,
    ): List<ReleaseNote> {
        return releases
            .asSequence()
            .filter { !it.draft && (includePrereleases || !it.prerelease) }
            .mapNotNull { release -> InterComVersion.parseTag(release.tagName)?.let { it to release } }
            .filter { (version, _) -> version.versionCode() in afterVersionCode + 1..upToVersionCode }
            .sortedByDescending { it.first }
            .distinctBy { it.first }
            .map { (version, release) -> ReleaseNote(version.name, version.versionCode(), release.body.orEmpty().take(MAX_NOTE_LENGTH)) }
            .filter { it.notes.isNotBlank() }
            .take(MAX_NOTES)
            .toList()
    }

    const val MAX_NOTES = 8
    const val MAX_NOTE_LENGTH = 6000

    fun apkName(version: InterComVersion) = "InterCom-Android-${version.name}-$ABI.apk"

    /**
     * Joins a selected release with its [manifest] (preferred) or the text of its `SHA256SUMS`.
     *
     * @throws IllegalArgumentException when what the release says about itself does not fit together.
     */
    fun toCandidate(
        selected: SelectedRelease,
        manifest: UpdateManifest?,
        sha256Sums: String?,
        packageName: String,
        currentVersionCode: Long,
    ): UpdateCandidate {
        val apk = selected.apk
        val versionCode: Long
        val sha256: String
        if (manifest != null) {
            require(manifest.schema == 1) { "android-update.json has schema ${manifest.schema}, this version reads 1" }
            require(manifest.packageName == packageName) { "android-update.json is for ${manifest.packageName}" }
            require(manifest.apk == apk.name) { "android-update.json names ${manifest.apk}, the release has ${apk.name}" }
            require(manifest.versionName == selected.version.name) {
                "android-update.json is for ${manifest.versionName}, the tag for ${selected.version.name}"
            }
            versionCode = manifest.versionCode
            sha256 = manifest.sha256
        } else {
            versionCode = selected.version.versionCode()
            sha256 = requireNotNull(sha256Sums?.let { Sha256.findInSums(it, apk.name) }) {
                "SHA256SUMS has no line for ${apk.name}"
            }
        }
        require(versionCode > currentVersionCode) { "version code $versionCode is not newer than $currentVersionCode" }
        require(Sha256.isHex(sha256)) { "not a SHA-256: $sha256" }
        return UpdateCandidate(
            versionName = selected.version.name,
            versionCode = versionCode,
            apkName = apk.name,
            apkUrl = apk.downloadUrl,
            apkSize = manifest?.size?.takeIf { it > 0 } ?: apk.size,
            sha256 = sha256.lowercase(),
            notes = manifest?.notes?.takeIf { it.isNotBlank() } ?: selected.release.body.orEmpty(),
            releaseUrl = selected.release.htmlUrl,
            minSdk = manifest?.minSdk,
        )
    }
}
