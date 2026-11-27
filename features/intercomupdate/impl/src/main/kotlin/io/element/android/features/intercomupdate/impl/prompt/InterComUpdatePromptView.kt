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
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.intercomupdate.impl.R
import io.element.android.features.intercomupdate.impl.actionRes
import io.element.android.features.intercomupdate.impl.install.InstallHint
import io.element.android.features.intercomupdate.impl.messageRes
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Mode
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Phase
import io.element.android.features.intercomupdate.impl.ui.releaseNotes
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.ProgressDialogType
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.components.dialogs.RetryDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun InterComUpdatePromptView(
    state: InterComUpdatePromptState,
    onOpenReleaseNotes: (String) -> Unit,
    onAllowInstallClick: () -> Unit,
    onOpenHintSettings: (InstallHint) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) state.eventSink(InterComUpdatePromptEvent.Resumed)
    }
    when (val mode = state.mode) {
        Mode.Hidden -> Unit
        is Mode.Offer -> ListDialog(
            modifier = modifier,
            title = stringResource(R.string.dialog_intercom_update_title, mode.versionName),
            submitText = stringResource(
                if (mode.needsInstallPermission) R.string.dialog_intercom_update_action_allow else R.string.dialog_intercom_update_action_update
            ),
            cancelText = stringResource(R.string.dialog_intercom_update_action_later),
            onSubmit = {
                if (mode.needsInstallPermission) onAllowInstallClick() else state.eventSink(InterComUpdatePromptEvent.UpdateNow)
            },
            onDismissRequest = { state.eventSink(InterComUpdatePromptEvent.Later) },
        ) {
            item {
                Text(
                    text = stringResource(
                        when {
                            mode.needsInstallPermission -> R.string.dialog_intercom_update_content_needs_permission
                            mode.isMetered -> R.string.dialog_intercom_update_content_metered
                            else -> R.string.dialog_intercom_update_content
                        }
                    ),
                    style = ElementTheme.typography.fontBodyMdRegular,
                )
            }
            if (mode.notes.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dialog_intercom_update_whats_new),
                        style = ElementTheme.typography.fontHeadingSmMedium,
                        color = ElementTheme.colors.textPrimary,
                    )
                }
                releaseNotes(mode.notes)
            }
            item {
                TextButton(
                    text = stringResource(R.string.screen_intercom_update_action_open_release),
                    size = ButtonSize.Medium,
                    onClick = { onOpenReleaseNotes(mode.releaseUrl) },
                )
            }
        }
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
        is Mode.Failed -> {
            val hint = mode.hint
            val action = hint?.actionRes()
            val message = stringResource(mode.error.messageRes())
            val content = if (hint != null) message + "\n\n" + stringResource(hint.messageRes()) else message
            if (hint != null && action != null) {
                ConfirmationDialog(
                    modifier = modifier,
                    title = stringResource(R.string.notification_intercom_update_failed_title),
                    content = content,
                    submitText = stringResource(action),
                    cancelText = stringResource(CommonStrings.action_cancel),
                    thirdButtonText = stringResource(R.string.screen_intercom_update_action_retry),
                    onSubmitClick = { onOpenHintSettings(hint) },
                    onThirdButtonClick = { state.eventSink(InterComUpdatePromptEvent.UpdateNow) },
                    onDismiss = { state.eventSink(InterComUpdatePromptEvent.Hide) },
                )
            } else {
                RetryDialog(
                    modifier = modifier,
                    content = content,
                    onRetry = { state.eventSink(InterComUpdatePromptEvent.UpdateNow) },
                    onDismiss = { state.eventSink(InterComUpdatePromptEvent.Hide) },
                )
            }
        }
        is Mode.WhatsNew -> ListDialog(
            modifier = modifier,
            title = stringResource(R.string.screen_intercom_update_whats_new_title, mode.versionName),
            submitText = stringResource(CommonStrings.action_ok),
            cancelText = stringResource(CommonStrings.action_close),
            onSubmit = { state.eventSink(InterComUpdatePromptEvent.DismissWhatsNew) },
            onDismissRequest = { state.eventSink(InterComUpdatePromptEvent.DismissWhatsNew) },
        ) {
            releaseNotes(mode.notes)
        }
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
        onOpenHintSettings = {},
    )
}
