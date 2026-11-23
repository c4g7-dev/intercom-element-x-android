/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.data

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext

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
