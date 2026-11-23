/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.prompt

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.Lifecycle
import io.element.android.features.intercomupdate.impl.R
import io.element.android.features.intercomupdate.impl.messageRes
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Mode
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Phase
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.ProgressDialogType
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.RetryDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent

@Composable
fun InterComUpdatePromptView(
    state: InterComUpdatePromptState,
    onOpenReleaseNotes: (String) -> Unit,
    onAllowInstallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) state.eventSink(InterComUpdatePromptEvent.Resumed)
    }
    when (val mode = state.mode) {
        Mode.Hidden -> Unit
        is Mode.Offer -> ConfirmationDialog(
            modifier = modifier,
            title = stringResource(R.string.dialog_intercom_update_title, mode.versionName),
            content = stringResource(
                when {
                    mode.needsInstallPermission -> R.string.dialog_intercom_update_content_needs_permission
                    mode.isMetered -> R.string.dialog_intercom_update_content_metered
                    else -> R.string.dialog_intercom_update_content
                }
            ),
            submitText = stringResource(
                if (mode.needsInstallPermission) R.string.dialog_intercom_update_action_allow else R.string.dialog_intercom_update_action_update
            ),
            cancelText = stringResource(R.string.dialog_intercom_update_action_later),
            thirdButtonText = stringResource(R.string.dialog_intercom_update_action_notes),
            onSubmitClick = {
                if (mode.needsInstallPermission) onAllowInstallClick() else state.eventSink(InterComUpdatePromptEvent.UpdateNow)
            },
            onThirdButtonClick = { onOpenReleaseNotes(mode.releaseUrl) },
            onDismiss = { state.eventSink(InterComUpdatePromptEvent.Later) },
        )
        is Mode.Progress -> ProgressDialog(
            modifier = modifier,
            text = when (val phase = mode.phase) {
                Phase.Starting -> stringResource(R.string.dialog_intercom_update_progress_starting, mode.versionName)
                is Phase.Downloading -> stringResource(
                    R.string.dialog_intercom_update_progress_downloading,
                    mode.versionName,
                    ((phase.progress ?: 0f) * 100).toInt(),
                )
                Phase.Installing -> stringResource(R.string.dialog_intercom_update_progress_installing, mode.versionName)
                Phase.AwaitingConfirmation -> stringResource(R.string.dialog_intercom_update_progress_confirm)
            },
            type = (mode.phase as? Phase.Downloading)?.progress?.let { ProgressDialogType.Determinate(it) } ?: ProgressDialogType.Indeterminate,
            showCancelButton = true,
            onDismissRequest = { state.eventSink(InterComUpdatePromptEvent.Hide) },
        )
        is Mode.Failed -> RetryDialog(
            modifier = modifier,
            content = stringResource(mode.error.messageRes()),
            onRetry = { state.eventSink(InterComUpdatePromptEvent.UpdateNow) },
            onDismiss = { state.eventSink(InterComUpdatePromptEvent.Hide) },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun InterComUpdatePromptViewPreview(
    @PreviewParameter(InterComUpdatePromptStatePreviewParam::class) state: InterComUpdatePromptState,
) = ElementPreview {
    InterComUpdatePromptView(
        state = state,
        onOpenReleaseNotes = {},
        onAllowInstallClick = {},
    )
}
