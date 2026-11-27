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
import io.element.android.features.intercomupdate.impl.data.NetworkWatcher
import io.element.android.features.intercomupdate.impl.logic.UpdatePolicy
import io.element.android.libraries.di.annotations.AppCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@ContributesIntoSet(AppScope::class)
class InterComUpdateStartupHook(
    private val updater: InterComUpdater,
    private val networkWatcher: NetworkWatcher,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : AppStartupHook {
    override suspend fun onAppStartup(activity: Activity) {
        updater.start()
        // Once per process, however often the activity is created.
        if (isObserving.compareAndSet(false, true)) {
            withContext(Dispatchers.Main.immediate) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    private var foreground: Job? = null

                    // While the app is on screen: a look now, the first of which is the app's start, one every half
                    // hour, and one when the network returns. The updater spaces them, see UpdatePolicy.
                    override fun onStart(owner: LifecycleOwner) {
                        foreground?.cancel()
                        foreground = coroutineScope.launch {
                            launch {
                                while (isActive) {
                                    updater.onAppOpened()
                                    delay(UpdatePolicy.FOREGROUND_LOOK_INTERVAL)
                                }
                            }
                            // The first value is the state of the network now, which the loop above covers.
                            networkWatcher.networkReturns.collect { updater.onAppOpened() }
                        }
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        foreground?.cancel()
                        foreground = null
                    }
                })
            }
        }
    }

    private companion object {
        val isObserving = AtomicBoolean(false)
    }
}
