/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.prompt

sealed interface InterComUpdatePromptEvent {
    data object UpdateNow : InterComUpdatePromptEvent

    /** Not now: the release is not brought up again for a day. */
    data object Later : InterComUpdatePromptEvent

    /** Closes the progress or the failure; the update itself goes on. */
    data object Hide : InterComUpdatePromptEvent

    /** "What's new" was closed, in whatever way. */
    data object DismissWhatsNew : InterComUpdatePromptEvent

    /** The app is on screen again: the time has moved on, and what the user allowed may have changed. */
    data object Resumed : InterComUpdatePromptEvent
}
