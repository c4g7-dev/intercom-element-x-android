/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.install

import io.element.android.features.intercomupdate.impl.logic.UpdateError

/** What to tell the user to do about an update that Android did not install, beyond the failure's own message. */
enum class InstallHint {
    /** Samsung's Auto Blocker stops installs from outside the Galaxy Store and the Play Store. */
    SamsungAutoBlocker,

    /** Make room. */
    Storage,

    /** This device cannot run the release; there is nothing to try again. */
    Incompatible,

    /** Another build is installed: one signed with another key, or a newer one. */
    Conflict;

    companion object {
        /**
         * The hint for [error] on a device made by [manufacturer] (`Build.MANUFACTURER`), or null when the message
         * says it all. Auto Blocker does not name itself to the app: on a Samsung device it is the likely reason of
         * every refusal that has no other explanation, and of a confirmation that was never given.
         */
        fun of(error: UpdateError, manufacturer: String?): InstallHint? {
            val isSamsung = manufacturer.orEmpty().trim().equals("samsung", ignoreCase = true)
            return when (error) {
                UpdateError.NotConfirmed -> SamsungAutoBlocker.takeIf { isSamsung }
                is UpdateError.Install -> when (InstallFailure.of(error.status)) {
                    InstallFailure.Storage -> Storage
                    InstallFailure.Incompatible -> Incompatible
                    InstallFailure.Conflict -> Conflict
                    InstallFailure.Blocked,
                    InstallFailure.Aborted,
                    InstallFailure.Timeout,
                    InstallFailure.Other -> SamsungAutoBlocker.takeIf { isSamsung }
                    InstallFailure.Invalid -> null
                }
                UpdateError.NoSpace -> Storage
                else -> null
            }
        }
    }
}
