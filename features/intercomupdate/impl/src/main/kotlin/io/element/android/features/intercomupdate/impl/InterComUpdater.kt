/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import android.content.Intent
import io.element.android.features.intercomupdate.impl.logic.UpdateState
import kotlinx.coroutines.flow.StateFlow

/** InterCom's updater: finds a newer release on GitHub, downloads its APK, checks it and has it installed. */
interface InterComUpdater {
    val state: StateFlow<UpdateState>

    /** False for builds that no release can replace (debug builds, with their own application id and key). */
    val isSupported: Boolean

    /** When the app starts: offers again what an earlier check found, and schedules the checks. */
    suspend fun start()

    /** Applies the settings to the scheduled checks. */
    suspend fun onSettingsChanged()

    /** Looks for a newer release. [manual] is true for "Check now", which needs no notification. */
    suspend fun check(manual: Boolean)

    /** Schedules [downloadAndInstall] for when the network allows it. */
    fun requestUpdate()

    /** Downloads the release that was found, checks it and hands it to the system's installer. */
    suspend fun downloadAndInstall()

    /** The installer's answer: a `PackageInstaller.STATUS_*`, its message, and what to show the user if it asks. */
    fun onInstallStatus(status: Int, message: String?, confirmation: Intent?)

    /** The first sign of life of a new version. */
    fun onAppReplaced()
}
