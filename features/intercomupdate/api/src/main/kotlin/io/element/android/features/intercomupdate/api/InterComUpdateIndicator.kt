/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.api

import kotlinx.coroutines.flow.Flow

/**
 * InterCom: whether a newer release than the running one is known, for the dot on the way to the settings and the
 * row at their top.
 */
interface InterComUpdateIndicator {
    val isUpdateAvailable: Flow<Boolean>

    /** The version name of that release, or null. */
    val availableVersion: Flow<String?>
}
