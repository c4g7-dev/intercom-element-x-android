/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.prompt

import io.element.android.features.intercomupdate.impl.logic.UpdateError

data class InterComUpdatePromptState(
    val mode: Mode,
    val eventSink: (InterComUpdatePromptEvent) -> Unit,
) {
    sealed interface Mode {
        data object Hidden : Mode

        /** "InterCom <version> is available". */
        data class Offer(
            val versionName: String,
            val releaseUrl: String,
            /** The user has not yet allowed InterCom to install apps. */
            val needsInstallPermission: Boolean,
            val isMetered: Boolean,
        ) : Mode

        /** The update the user asked for is under way. */
        data class Progress(val versionName: String, val phase: Phase) : Mode

        data class Failed(val error: UpdateError) : Mode
    }

    sealed interface Phase {
        /** Asked for, not yet begun: waiting for a connection. */
        data object Starting : Phase

        /** [progress] in 0..1, or null when the size is not known. */
        data class Downloading(val progress: Float?) : Phase
        data object Installing : Phase
        data object AwaitingConfirmation : Phase
    }
}
