/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A release as the GitHub API lists it; only what the updater reads. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
)

/**
 * `android-update.json`, published with every release of InterCom for Android (written by
 * `tools/android-release.sh` of the InterCom repository, documented in its docs/BUILDING.md).
 */
@Serializable
data class UpdateManifest(
    val schema: Int = 1,
    @SerialName("package") val packageName: String,
    val versionCode: Long,
    val versionName: String,
    /** The file name of the APK among the release's files. */
    val apk: String,
    /** SHA-256 of the APK, in hexadecimal. */
    val sha256: String,
    val size: Long = 0,
    val abi: String? = null,
    val minSdk: Int? = null,
    val notes: String = "",
)

/** A release that can be installed over the running app, with all that is needed to fetch and check it. */
@Serializable
data class UpdateCandidate(
    val versionName: String,
    val versionCode: Long,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lowercase hexadecimal. */
    val sha256: String,
    val notes: String = "",
    val releaseUrl: String? = null,
    val minSdk: Int? = null,
    /** The notes of this release and of every release between it and the running app, newest first. */
    val notesHistory: List<ReleaseNote> = emptyList(),
) {
    /** [notesHistory], or for a candidate that was stored by a version without it, the release's own notes. */
    fun history(): List<ReleaseNote> {
        return notesHistory.ifEmpty { listOf(ReleaseNote(versionName, versionCode, notes)) }.filter { it.notes.isNotBlank() }
    }
}

/** The notes of one release: the Markdown of its GitHub release, or the plain text of its `android-update.json`. */
@Serializable
data class ReleaseNote(
    val versionName: String,
    val versionCode: Long,
    val notes: String,
)

/** What is shown once after an update: the running version and the notes of every release the update brought. */
@Serializable
data class WhatsNew(
    val versionName: String,
    val versionCode: Long,
    val notes: List<ReleaseNote>,
)
