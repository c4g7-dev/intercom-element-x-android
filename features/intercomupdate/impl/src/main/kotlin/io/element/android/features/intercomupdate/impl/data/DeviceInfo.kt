/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.data

import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding

/** What the updater needs to know about the device: who made it, for the hints about a failed installation. */
interface DeviceInfo {
    /** `Build.MANUFACTURER`. */
    val manufacturer: String
    val sdkInt: Int
}

@ContributesBinding(AppScope::class)
class AndroidDeviceInfo : DeviceInfo {
    override val manufacturer: String = Build.MANUFACTURER.orEmpty()
    override val sdkInt: Int = Build.VERSION.SDK_INT
}
