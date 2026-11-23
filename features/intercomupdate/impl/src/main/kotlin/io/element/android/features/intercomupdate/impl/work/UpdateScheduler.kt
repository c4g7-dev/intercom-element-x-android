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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import java.util.concurrent.TimeUnit

/** Runs the updater's work when the network allows it. */
interface UpdateScheduler {
    /** A check every twelve hours, or none when the automatic checks are off. */
    fun schedulePeriodicCheck(settings: UpdateSettings)

    /** A check shortly after the app has started. */
    fun checkSoon(settings: UpdateSettings)

    /** The download and installation of the release that was found. */
    fun enqueueUpdate(settings: UpdateSettings)
}

@ContributesBinding(AppScope::class)
class DefaultUpdateScheduler(
    lazyWorkManager: Lazy<WorkManager>,
) : UpdateScheduler {
    private val workManager by lazyWorkManager

    override fun schedulePeriodicCheck(settings: UpdateSettings) {
        if (!settings.checkAutomatically) {
            workManager.cancelUniqueWork(PERIODIC_CHECK)
            workManager.cancelUniqueWork(START_CHECK)
            return
        }
        val request = PeriodicWorkRequestBuilder<InterComUpdateCheckWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(settings.constraints())
            .setInitialDelay(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
            .build()
        // UPDATE keeps the running period and takes the new constraints.
        workManager.enqueueUniquePeriodicWork(PERIODIC_CHECK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun checkSoon(settings: UpdateSettings) {
        val request = OneTimeWorkRequestBuilder<InterComUpdateCheckWorker>()
            .setConstraints(settings.constraints())
            .setInitialDelay(START_CHECK_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(START_CHECK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun enqueueUpdate(settings: UpdateSettings) {
        val request = OneTimeWorkRequestBuilder<InterComUpdateInstallWorker>()
            .setConstraints(settings.constraints())
            .build()
        workManager.enqueueUniqueWork(INSTALL, ExistingWorkPolicy.KEEP, request)
    }

    private fun UpdateSettings.constraints() = Constraints.Builder()
        .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
        .build()

    private companion object {
        const val PERIODIC_CHECK = "intercom-update-check"
        const val START_CHECK = "intercom-update-check-start"
        const val INSTALL = "intercom-update-install"
        const val CHECK_INTERVAL_HOURS = 12L
        const val START_CHECK_DELAY_SECONDS = 20L
    }
}
