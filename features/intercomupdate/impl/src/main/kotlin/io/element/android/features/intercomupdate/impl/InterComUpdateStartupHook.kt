/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.element.android.features.enterprise.api.AppStartupHook
import io.element.android.libraries.di.annotations.AppCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@ContributesIntoSet(AppScope::class)
class InterComUpdateStartupHook(
    private val updater: InterComUpdater,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : AppStartupHook {
    override suspend fun onAppStartup(activity: Activity) {
        updater.start()
        // Once per process, however often the activity is created: every time the app comes to the screen, the
        // first of which is now.
        if (isObserving.compareAndSet(false, true)) {
            withContext(Dispatchers.Main.immediate) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        coroutineScope.launch { updater.onAppOpened() }
                    }
                })
            }
        }
    }

    private companion object {
        val isObserving = AtomicBoolean(false)
    }
}
