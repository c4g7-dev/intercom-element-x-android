/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.prompt

import android.content.pm.PackageInstaller
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.intercomupdate.impl.install.InstallHint
import io.element.android.features.intercomupdate.impl.logic.ReleaseNote
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Mode
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Phase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

open class InterComUpdatePromptStatePreviewParam : PreviewParameterProvider<InterComUpdatePromptState> {
    override val values: Sequence<InterComUpdatePromptState>
        get() = sequenceOf(
            anInterComUpdatePromptState(anOffer()),
            anInterComUpdatePromptState(anOffer(isMetered = true)),
            anInterComUpdatePromptState(anOffer(needsInstallPermission = true)),
            anInterComUpdatePromptState(Mode.Progress(VERSION, Phase.Starting)),
            anInterComUpdatePromptState(Mode.Progress(VERSION, Phase.Downloading(0.4f))),
            anInterComUpdatePromptState(Mode.Progress(VERSION, Phase.Installing)),
            anInterComUpdatePromptState(Mode.Progress(VERSION, Phase.AwaitingConfirmation)),
            anInterComUpdatePromptState(Mode.Failed(UpdateError.Install(PackageInstaller.STATUS_FAILURE_STORAGE, null), InstallHint.Storage)),
            anInterComUpdatePromptState(Mode.Failed(UpdateError.Install(PackageInstaller.STATUS_FAILURE_BLOCKED, null), InstallHint.SamsungAutoBlocker)),
            anInterComUpdatePromptState(Mode.Failed(UpdateError.Network(null), null)),
            anInterComUpdatePromptState(anOffer(notes = someReleaseNotes())),
            anInterComUpdatePromptState(Mode.WhatsNew(VERSION, someReleaseNotes())),
        )
}

private const val VERSION = "26.11.1-intercom.14"

fun someReleaseNotes(): ImmutableList<ReleaseNote> = persistentListOf(
    ReleaseNote(
        versionName = VERSION,
        versionCode = 2_026_110_134,
        notes = """
            `android-v26.11.1-intercom.14`, based on Element X Android 26.11.1.

            - **Deafen** in calls, next to the microphone: nobody is heard, and
              the microphone is muted with it.
            - Pictures sent together are shown as **one gallery**.
              ![A gallery](https://example.org/gallery.png)
        """.trimIndent(),
    ),
    ReleaseNote(
        versionName = "26.11.1-intercom.7",
        versionCode = 2_026_110_127,
        notes = "- **Native calls connect.** Every call ended half a second after it opened.",
    ),
)

fun anOffer(
    needsInstallPermission: Boolean = false,
    isMetered: Boolean = false,
    notes: ImmutableList<ReleaseNote> = persistentListOf(),
) = Mode.Offer(
    versionName = VERSION,
    releaseUrl = "https://github.com/c4g7-dev/InterCom/releases",
    needsInstallPermission = needsInstallPermission,
    isMetered = isMetered,
    notes = notes,
)

fun anInterComUpdatePromptState(
    mode: Mode = Mode.Hidden,
    eventSink: (InterComUpdatePromptEvent) -> Unit = {},
) = InterComUpdatePromptState(mode = mode, eventSink = eventSink)
