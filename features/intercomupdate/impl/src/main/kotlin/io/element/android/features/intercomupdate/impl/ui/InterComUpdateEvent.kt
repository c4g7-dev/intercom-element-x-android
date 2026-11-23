/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.ui

sealed interface InterComUpdateEvent {
    data class SetCheckAutomatically(val enabled: Boolean) : InterComUpdateEvent
    data class SetAllowMetered(val enabled: Boolean) : InterComUpdateEvent
    data class SetIncludePrereleases(val enabled: Boolean) : InterComUpdateEvent
    data object CheckNow : InterComUpdateEvent
    data object Update : InterComUpdateEvent

    /** The screen is shown again: what the user allowed in the system's settings may have changed. */
    data object Resumed : InterComUpdateEvent
}
