/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.getSystemService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun interface NetworkMeter {
    /** Whether the connection in use is one that is paid by volume, as mobile data usually is. */
    fun isMetered(): Boolean
}

@ContributesBinding(AppScope::class)
class DefaultNetworkMeter(
    @ApplicationContext private val context: Context,
) : NetworkMeter {
    override fun isMetered(): Boolean {
        return runCatchingExceptions { context.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered }.getOrNull() ?: false
    }
}

interface NetworkWatcher {
    /** Emits each time the device gets a network after having had none; nothing for the network it starts with. */
    val networkReturns: Flow<Unit>
}

@ContributesBinding(AppScope::class)
class DefaultNetworkWatcher(
    @ApplicationContext private val context: Context,
) : NetworkWatcher {
    override val networkReturns: Flow<Unit> = callbackFlow {
        val manager = context.getSystemService<ConnectivityManager>()
        var known = manager?.let { runCatchingExceptions { it.activeNetwork }.getOrNull() } != null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!known) trySend(Unit)
                known = true
            }

            override fun onLost(network: Network) {
                known = false
            }
        }
        val registered = manager != null && runCatchingExceptions { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        awaitClose { if (registered) runCatchingExceptions { manager?.unregisterNetworkCallback(callback) } }
    }
}
