/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import android.app.Activity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.element.android.features.enterprise.api.AppStartupHook

@ContributesIntoSet(AppScope::class)
class InterComUpdateStartupHook(
    private val updater: InterComUpdater,
) : AppStartupHook {
    override suspend fun onAppStartup(activity: Activity) = updater.start()
}
