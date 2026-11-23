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

    /**
     * Every time the app comes to the screen: looks for a newer release, at most once in
     * [io.element.android.features.intercomupdate.impl.logic.UpdatePolicy.OPEN_CHECK_INTERVAL], on any connection.
     */
    suspend fun onAppOpened()

    /** Applies the settings to the scheduled checks. */
    suspend fun onSettingsChanged()

    /**
     * Looks for a newer release. One that is found is installed automatically where the settings and the system
     * allow, and is otherwise on offer; a [CheckTrigger.Background] check says so in a notification.
     */
    suspend fun check(trigger: CheckTrigger)

    /** The user asks for the update: [downloadAndInstall] as soon as there is any connection. */
    fun requestUpdate()

    /** "Later": the release on offer is not brought up in a dialog for a day. */
    suspend fun snooze()

    /**
     * Downloads the release that was found, checks it and hands it to the system's installer.
     * Not [userInitiated], it does not interrupt a call: it returns [UpdateRun.Postponed] to be run again later.
     */
    suspend fun downloadAndInstall(userInitiated: Boolean): UpdateRun

    /** The installer's answer: a `PackageInstaller.STATUS_*`, its message, and what to show the user if it asks. */
    fun onInstallStatus(status: Int, message: String?, confirmation: Intent?)

    /** The first sign of life of a new version. */
    fun onAppReplaced()
}

enum class CheckTrigger {
    /** "Check now" in the settings. */
    Manual,

    /** The app came to the screen. */
    AppOpen,

    /** The periodic check, with the app possibly not on screen. */
    Background,
}

enum class UpdateRun {
    /** It ran to its end, whatever that is; the updater's state tells. */
    Done,

    /** It waits for a call to end. */
    Postponed,
}
