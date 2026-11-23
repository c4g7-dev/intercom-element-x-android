/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.install

import android.content.pm.PackageInstaller

/** The installer's failure statuses, by what they mean to the user. */
enum class InstallFailure {
    /** The user, or the system on their behalf, said no. */
    Aborted,

    /** Something on the device forbids it: a device policy, Play Protect, Advanced Protection. */
    Blocked,

    /** The installed app is signed with another key, or is newer. */
    Conflict,

    /** This device cannot run the APK: Android version or processor. */
    Incompatible,

    /** The system finds the file broken. */
    Invalid,

    /** Not enough free storage. */
    Storage,

    /** The confirmation was not answered in time. */
    Timeout,

    Other;

    companion object {
        /** `PackageInstaller.STATUS_FAILURE_TIMEOUT`, API level 34. */
        private const val STATUS_FAILURE_TIMEOUT = 8

        fun of(status: Int): InstallFailure = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> Aborted
            PackageInstaller.STATUS_FAILURE_BLOCKED -> Blocked
            PackageInstaller.STATUS_FAILURE_CONFLICT -> Conflict
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Incompatible
            PackageInstaller.STATUS_FAILURE_INVALID -> Invalid
            PackageInstaller.STATUS_FAILURE_STORAGE -> Storage
            STATUS_FAILURE_TIMEOUT -> Timeout
            else -> Other
        }
    }
}
