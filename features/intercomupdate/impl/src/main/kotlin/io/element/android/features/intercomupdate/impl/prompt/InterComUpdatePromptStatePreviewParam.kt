/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.prompt

import android.content.pm.PackageInstaller
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Mode
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Phase

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
            anInterComUpdatePromptState(Mode.Failed(UpdateError.Install(PackageInstaller.STATUS_FAILURE_STORAGE, null))),
        )
}

private const val VERSION = "26.11.1-intercom.6"

fun anOffer(
    needsInstallPermission: Boolean = false,
    isMetered: Boolean = false,
) = Mode.Offer(
    versionName = VERSION,
    releaseUrl = "https://github.com/c4g7-dev/InterCom/releases",
    needsInstallPermission = needsInstallPermission,
    isMetered = isMetered,
)

fun anInterComUpdatePromptState(
    mode: Mode = Mode.Hidden,
    eventSink: (InterComUpdatePromptEvent) -> Unit = {},
) = InterComUpdatePromptState(mode = mode, eventSink = eventSink)
