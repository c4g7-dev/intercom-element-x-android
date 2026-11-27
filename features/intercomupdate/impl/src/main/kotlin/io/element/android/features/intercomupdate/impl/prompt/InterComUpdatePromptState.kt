/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.prompt

import io.element.android.features.intercomupdate.impl.install.InstallHint
import io.element.android.features.intercomupdate.impl.logic.ReleaseNote
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import kotlinx.collections.immutable.ImmutableList

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
            /** What is new in this release and in those between it and the running app, newest first. */
            val notes: ImmutableList<ReleaseNote>,
        ) : Mode

        /** The update the user asked for is under way. */
        data class Progress(val versionName: String, val phase: Phase) : Mode

        /** [hint]: what the user can do about it, when that is more than trying again. */
        data class Failed(val error: UpdateError, val hint: InstallHint?) : Mode

        /** "What's new in InterCom <version>", once after an update. */
        data class WhatsNew(val versionName: String, val notes: ImmutableList<ReleaseNote>) : Mode
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
