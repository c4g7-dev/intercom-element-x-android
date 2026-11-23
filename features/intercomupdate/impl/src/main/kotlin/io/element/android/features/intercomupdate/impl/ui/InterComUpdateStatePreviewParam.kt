/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.ui

import android.content.pm.PackageInstaller
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.logic.ApkRejection
import io.element.android.features.intercomupdate.impl.logic.UpdateCandidate
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import io.element.android.features.intercomupdate.impl.logic.UpdateState

open class InterComUpdateStatePreviewParam : PreviewParameterProvider<InterComUpdateState> {
    override val values: Sequence<InterComUpdateState>
        get() = sequenceOf(
            anInterComUpdateState(),
            anInterComUpdateState(updateState = UpdateState.UpToDate),
            anInterComUpdateState(updateState = UpdateState.Available(anUpdateCandidate())),
            anInterComUpdateState(updateState = UpdateState.Available(anUpdateCandidate()), isAllowedToInstall = false),
            anInterComUpdateState(updateState = UpdateState.Available(anUpdateCandidate()), isMetered = true),
            anInterComUpdateState(updateState = UpdateState.Downloading(anUpdateCandidate(), 0.4f)),
            anInterComUpdateState(updateState = UpdateState.AwaitingConfirmation(anUpdateCandidate())),
            anInterComUpdateState(
                updateState = UpdateState.Failed(
                    UpdateError.Install(PackageInstaller.STATUS_FAILURE_STORAGE, "INSTALL_FAILED_INSUFFICIENT_STORAGE"),
                    anUpdateCandidate(),
                ),
            ),
            anInterComUpdateState(updateState = UpdateState.Failed(UpdateError.BadApk(ApkRejection.SignatureMismatch), anUpdateCandidate())),
            anInterComUpdateState(isSupported = false),
        )
}

fun anUpdateCandidate(
    versionName: String = "26.11.1-intercom.5",
    versionCode: Long = 2_026_110_125,
    notes: String = "Calls reconnect faster after the network changes.",
) = UpdateCandidate(
    versionName = versionName,
    versionCode = versionCode,
    apkName = "InterCom-Android-$versionName-arm64-v8a.apk",
    apkUrl = "https://github.com/c4g7-dev/InterCom/releases/download/android-v$versionName/InterCom-Android-$versionName-arm64-v8a.apk",
    apkSize = 130_000_000,
    sha256 = "0".repeat(64),
    notes = notes,
)

fun anInterComUpdateState(
    isSupported: Boolean = true,
    installedVersion: String = "26.11.1-intercom.4",
    settings: UpdateSettings = UpdateSettings(),
    updateState: UpdateState = UpdateState.Idle,
    isAllowedToInstall: Boolean = true,
    isMetered: Boolean = false,
    eventSink: (InterComUpdateEvent) -> Unit = {},
) = InterComUpdateState(
    isSupported = isSupported,
    installedVersion = installedVersion,
    settings = settings,
    updateState = updateState,
    isAllowedToInstall = isAllowedToInstall,
    isMetered = isMetered,
    eventSink = eventSink,
)
