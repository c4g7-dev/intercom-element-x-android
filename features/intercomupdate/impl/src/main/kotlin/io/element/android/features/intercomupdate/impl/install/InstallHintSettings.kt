/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.install

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.element.android.libraries.core.extensions.runCatchingExceptions
import timber.log.Timber

/** Samsung's Auto Blocker ("Rampart"). It has no documented way in, so its own launch intent is tried first. */
private const val SAMSUNG_AUTO_BLOCKER_PACKAGE = "com.samsung.android.rampart"

/**
 * Opens the screen of the system's settings that [hint] sends the user to, or the nearest one there is: for
 * Auto Blocker its own screen where the system gives one, else "Security and privacy", which lists it.
 */
fun Context.openSettingsFor(hint: InstallHint) {
    val candidates = when (hint) {
        InstallHint.SamsungAutoBlocker -> listOfNotNull(
            runCatchingExceptions { packageManager.getLaunchIntentForPackage(SAMSUNG_AUTO_BLOCKER_PACKAGE) }.getOrNull(),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        InstallHint.Storage -> listOf(
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        InstallHint.Incompatible,
        InstallHint.Conflict -> emptyList()
    }
    for (intent in candidates) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            Timber.w(e, "Could not open $intent")
        }
    }
}
