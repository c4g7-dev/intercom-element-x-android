/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.work

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.logic.UpdatePolicy
import java.util.concurrent.TimeUnit

/** Runs the updater's work when the network allows it. */
interface UpdateScheduler {
    /** A check every twelve hours, or none when the automatic checks are off. */
    fun schedulePeriodicCheck(settings: UpdateSettings)

    /**
     * The download and installation of the release that was found: [userInitiated] on any connection and in place
     * of an automatic one that may be waiting for Wi-Fi, otherwise on the connection the settings allow.
     */
    fun enqueueUpdate(settings: UpdateSettings, userInitiated: Boolean)
}

@ContributesBinding(AppScope::class)
class DefaultUpdateScheduler(
    lazyWorkManager: Lazy<WorkManager>,
) : UpdateScheduler {
    private val workManager by lazyWorkManager

    override fun schedulePeriodicCheck(settings: UpdateSettings) {
        if (!settings.checkAutomatically) {
            workManager.cancelUniqueWork(PERIODIC_CHECK)
            return
        }
        val request = PeriodicWorkRequestBuilder<InterComUpdateCheckWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
            // A check is small: it runs on any connection. Only downloads mind the kind.
            .setConstraints(constraints(unmetered = false))
            .setInitialDelay(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
            .build()
        // UPDATE keeps the running period and takes the new constraints.
        workManager.enqueueUniquePeriodicWork(PERIODIC_CHECK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun enqueueUpdate(settings: UpdateSettings, userInitiated: Boolean) {
        val request = OneTimeWorkRequestBuilder<InterComUpdateInstallWorker>()
            .setConstraints(constraints(unmetered = UpdatePolicy.needsUnmetered(settings, userInitiated)))
            .setInputData(workDataOf(InterComUpdateInstallWorker.USER_INITIATED to userInitiated))
            .build()
        val policy = if (userInitiated) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(INSTALL, policy, request)
    }

    private fun constraints(unmetered: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    private companion object {
        const val PERIODIC_CHECK = "intercom-update-check"
        const val INSTALL = "intercom-update-install"
        const val CHECK_INTERVAL_HOURS = 12L
    }
}
