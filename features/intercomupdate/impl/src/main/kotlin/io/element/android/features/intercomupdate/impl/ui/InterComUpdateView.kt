/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.intercomupdate.impl.R
import io.element.android.features.intercomupdate.impl.actionRes
import io.element.android.features.intercomupdate.impl.detail
import io.element.android.features.intercomupdate.impl.install.InstallHint
import io.element.android.features.intercomupdate.impl.logic.UpdateState
import io.element.android.features.intercomupdate.impl.messageRes
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent

@Composable
fun InterComUpdateView(
    state: InterComUpdateState,
    onBackClick: () -> Unit,
    onAllowInstallClick: () -> Unit,
    onOpenHintSettings: (InstallHint) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) state.eventSink(InterComUpdateEvent.Resumed)
    }
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.screen_intercom_update_title),
    ) {
        ListItem(
            content = { Text(stringResource(R.string.screen_intercom_update_installed_version, state.installedVersion)) },
            supportingContent = {
                Text(if (state.isSupported) statusText(state.updateState) else stringResource(R.string.screen_intercom_update_unsupported))
            },
        )
        if (state.isSupported) {
            UpdateSection(state = state, onAllowInstallClick = onAllowInstallClick, onOpenHintSettings = onOpenHintSettings)
            SettingsSection(state = state)
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                text = stringResource(R.string.screen_intercom_update_explanation),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun statusText(updateState: UpdateState): String = when (updateState) {
    UpdateState.Idle -> stringResource(R.string.screen_intercom_update_status_idle)
    is UpdateState.Checking -> stringResource(R.string.screen_intercom_update_status_checking)
    UpdateState.UpToDate -> stringResource(R.string.screen_intercom_update_status_up_to_date)
    is UpdateState.Available -> stringResource(R.string.screen_intercom_update_status_available, updateState.candidate.versionName)
    is UpdateState.Downloading -> stringResource(R.string.screen_intercom_update_status_downloading, updateState.candidate.versionName)
    is UpdateState.Verifying -> stringResource(R.string.screen_intercom_update_status_verifying)
    is UpdateState.Installing -> stringResource(R.string.screen_intercom_update_status_installing)
    is UpdateState.AwaitingConfirmation -> stringResource(R.string.screen_intercom_update_status_awaiting_confirmation)
    is UpdateState.Failed -> stringResource(updateState.error.messageRes())
}

@Composable
private fun UpdateSection(
    state: InterComUpdateState,
    onAllowInstallClick: () -> Unit,
    onOpenHintSettings: (InstallHint) -> Unit,
) {
    val updateState = state.updateState
    val candidate = updateState.candidate
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (updateState is UpdateState.Failed) {
            updateState.error.detail()?.let { detail ->
                Text(
                    modifier = Modifier.padding(bottom = 8.dp),
                    text = detail,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
        val hint = state.installHint
        if (hint != null) {
            Text(
                modifier = Modifier.padding(bottom = 8.dp),
                text = stringResource(hint.messageRes()),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textPrimary,
            )
            hint.actionRes()?.let { action ->
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    text = stringResource(action),
                    onClick = { onOpenHintSettings(hint) },
                )
            }
        }
        if (updateState is UpdateState.Downloading) {
            val progress = updateState.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
        }
        if (candidate != null && !updateState.isBusy && updateState !is UpdateState.Checking) {
            if (state.isAllowedToInstall) {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    text = stringResource(
                        if (updateState is UpdateState.Failed) {
                            R.string.screen_intercom_update_action_retry
                        } else {
                            R.string.screen_intercom_update_action_update
                        }
                    ),
                    onClick = { state.eventSink(InterComUpdateEvent.Update) },
                )
                if (state.isMetered && !state.settings.allowMetered && state.settings.autoInstall) {
                    Text(
                        text = stringResource(R.string.screen_intercom_update_waiting_for_unmetered),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    text = stringResource(R.string.screen_intercom_update_action_allow_install),
                    onClick = onAllowInstallClick,
                )
                Text(
                    text = stringResource(R.string.screen_intercom_update_allow_install_subtitle),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            text = stringResource(R.string.screen_intercom_update_action_check),
            enabled = !updateState.isBusy && updateState !is UpdateState.Checking,
            showProgress = updateState is UpdateState.Checking,
            onClick = { state.eventSink(InterComUpdateEvent.CheckNow) },
        )
    }
    val notes = remember(candidate) { candidate?.history().orEmpty() }
    if (notes.isNotEmpty()) {
        PreferenceCategory(title = stringResource(R.string.screen_intercom_update_release_notes)) {
            notes.forEach { note ->
                ReleaseNoteView(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    note = note,
                    showVersion = notes.size > 1,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(state: InterComUpdateState) {
    PreferenceCategory {
        PreferenceSwitch(
            title = stringResource(R.string.screen_intercom_update_check_automatically_title),
            subtitle = stringResource(R.string.screen_intercom_update_check_automatically_subtitle),
            isChecked = state.settings.checkAutomatically,
            onCheckedChange = { state.eventSink(InterComUpdateEvent.SetCheckAutomatically(it)) },
        )
        PreferenceSwitch(
            title = stringResource(R.string.screen_intercom_update_auto_install_title),
            subtitle = stringResource(R.string.screen_intercom_update_auto_install_subtitle),
            isChecked = state.settings.autoInstall,
            onCheckedChange = { state.eventSink(InterComUpdateEvent.SetAutoInstall(it)) },
        )
        PreferenceSwitch(
            title = stringResource(R.string.screen_intercom_update_allow_metered_title),
            subtitle = stringResource(R.string.screen_intercom_update_allow_metered_subtitle),
            isChecked = state.settings.allowMetered,
            onCheckedChange = { state.eventSink(InterComUpdateEvent.SetAllowMetered(it)) },
        )
        PreferenceSwitch(
            title = stringResource(R.string.screen_intercom_update_prereleases_title),
            subtitle = stringResource(R.string.screen_intercom_update_prereleases_subtitle),
            isChecked = state.settings.includePrereleases,
            onCheckedChange = { state.eventSink(InterComUpdateEvent.SetIncludePrereleases(it)) },
        )
        PreferenceSwitch(
            title = stringResource(R.string.screen_intercom_update_whats_new_setting_title),
            subtitle = stringResource(R.string.screen_intercom_update_whats_new_setting_subtitle),
            isChecked = state.settings.showWhatsNew,
            onCheckedChange = { state.eventSink(InterComUpdateEvent.SetShowWhatsNew(it)) },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun InterComUpdateViewPreview(@PreviewParameter(InterComUpdateStatePreviewParam::class) state: InterComUpdateState) = ElementPreview {
    InterComUpdateView(
        state = state,
        onBackClick = {},
        onAllowInstallClick = {},
        onOpenHintSettings = {},
    )
}
