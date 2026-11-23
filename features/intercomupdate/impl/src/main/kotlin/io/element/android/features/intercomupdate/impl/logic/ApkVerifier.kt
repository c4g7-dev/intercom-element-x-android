/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

/** What Android reads from an APK, installed or in a file. [signers] are the SHA-256 of its signing certificates. */
data class ApkInfo(
    val packageName: String,
    val versionCode: Long,
    val signers: Set<String>,
)

enum class ApkRejection {
    /** Android could not read the file as an APK. */
    Unreadable,

    /** It is another app. */
    WrongPackage,

    /** It is not the version the release announced. */
    WrongVersion,

    /** It is not newer than the installed app. */
    NotNewer,

    /** The certificates of the file or of the installed app could not be read. */
    NoSignature,

    /** It is signed with another key than the installed app. */
    SignatureMismatch,
}

object ApkVerifier {
    /**
     * Decides whether the downloaded APK [archive] may be handed to the installer over [installed].
     * Returns null when it may, the reason when not.
     *
     * Android makes the same signature check when it installs. Doing it first means a file from somebody else
     * is never offered to the system, and the user is told why.
     */
    fun check(archive: ApkInfo?, installed: ApkInfo?, expectedVersionCode: Long): ApkRejection? {
        return when {
            archive == null -> ApkRejection.Unreadable
            installed == null -> ApkRejection.NoSignature
            archive.packageName != installed.packageName -> ApkRejection.WrongPackage
            archive.versionCode != expectedVersionCode -> ApkRejection.WrongVersion
            archive.versionCode <= installed.versionCode -> ApkRejection.NotNewer
            archive.signers.isEmpty() || installed.signers.isEmpty() -> ApkRejection.NoSignature
            archive.signers != installed.signers -> ApkRejection.SignatureMismatch
            else -> null
        }
    }
}

/** Reads [ApkInfo] from the system. */
interface ApkInfoReader {
    /** The running app, or null when its certificates cannot be read. */
    fun installed(): ApkInfo?

    /** The APK at [path], or null when Android cannot parse it. */
    fun archive(path: String): ApkInfo?
}
