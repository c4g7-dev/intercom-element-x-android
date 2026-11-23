/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.call.api.CurrentCall
import io.element.android.features.call.api.CurrentCallService

fun interface CallMonitor {
    /** Whether a call is going on in the app, which an update would end by closing the app. */
    fun isInCall(): Boolean
}

@ContributesBinding(AppScope::class)
class DefaultCallMonitor(
    private val currentCallService: CurrentCallService,
) : CallMonitor {
    override fun isInCall(): Boolean = currentCallService.currentCall.value != CurrentCall.None
}
