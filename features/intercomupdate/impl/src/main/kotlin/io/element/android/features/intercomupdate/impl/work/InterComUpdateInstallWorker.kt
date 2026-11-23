/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import io.element.android.features.intercomupdate.impl.InterComUpdater
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.workmanager.api.di.MetroWorkerFactory
import io.element.android.libraries.workmanager.api.di.WorkerKey

/** Downloads the release that was found and hands it to the installer, once the network allows the download. */
@AssistedInject
class InterComUpdateInstallWorker(
    @Assisted params: WorkerParameters,
    @ApplicationContext context: Context,
    private val updater: InterComUpdater,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // The outcome is in the updater's state and in a notification; there is nothing for WorkManager to retry.
        updater.downloadAndInstall()
        return Result.success()
    }

    @ContributesIntoMap(AppScope::class, binding = binding<MetroWorkerFactory.WorkerInstanceFactory<*>>())
    @WorkerKey(InterComUpdateInstallWorker::class)
    @AssistedFactory
    interface Factory : MetroWorkerFactory.WorkerInstanceFactory<InterComUpdateInstallWorker>
}
