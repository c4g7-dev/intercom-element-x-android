/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.prompt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.intercomupdate.impl.InterComUpdater
import io.element.android.features.intercomupdate.impl.data.InterComUpdateStore
import io.element.android.features.intercomupdate.impl.data.NetworkMeter
import io.element.android.features.intercomupdate.impl.data.Snooze
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.install.ApkInstaller
import io.element.android.features.intercomupdate.impl.logic.UpdatePolicy
import io.element.android.features.intercomupdate.impl.logic.UpdateState
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Mode
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Phase
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Inject
class InterComUpdatePromptPresenter(
    private val updater: InterComUpdater,
    private val store: InterComUpdateStore,
    private val installer: ApkInstaller,
    private val networkMeter: NetworkMeter,
    private val clock: SystemClock,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : Presenter<InterComUpdatePromptState> {
    @Composable
    override fun present(): InterComUpdatePromptState {
        val updateState by updater.state.collectAsState()
        val settings by remember { store.settings }.collectAsState(initial = UpdateSettings())
        // Nothing is offered before the stored snooze is known.
        val snooze by remember { store.snooze }.collectAsState(initial = null)
        var now by remember { mutableLongStateOf(clock.epochMillis()) }
        var isAllowedToInstall by remember { mutableStateOf(installer.isAllowedToInstall()) }
        var isMetered by remember { mutableStateOf(networkMeter.isMetered()) }
        // The user said "Update now": from then on the dialog shows how that goes, until it is hidden.
        var isFollowingUpdate by remember { mutableStateOf(false) }
        val autoInstallFailedVersionCode by produceState(0L, updateState) { value = store.autoInstallFailedVersionCode() }

        fun handleEvent(event: InterComUpdatePromptEvent) {
            when (event) {
                InterComUpdatePromptEvent.UpdateNow -> {
                    isAllowedToInstall = installer.isAllowedToInstall()
                    if (isAllowedToInstall) {
                        isFollowingUpdate = true
                        updater.requestUpdate()
                    }
                }
                InterComUpdatePromptEvent.Later -> appCoroutineScope.launch { updater.snooze() }
                InterComUpdatePromptEvent.Hide -> isFollowingUpdate = false
                InterComUpdatePromptEvent.Resumed -> {
                    now = clock.epochMillis()
                    isAllowedToInstall = installer.isAllowedToInstall()
                    isMetered = networkMeter.isMetered()
                }
            }
        }

        val mode = mode(
            updateState = updateState,
            settings = settings,
            snooze = snooze,
            now = now,
            isAllowedToInstall = isAllowedToInstall,
            isMetered = isMetered,
            isFollowingUpdate = isFollowingUpdate,
            autoInstallFailedVersionCode = autoInstallFailedVersionCode,
        )
        return InterComUpdatePromptState(
            mode = if (updater.isSupported) mode else Mode.Hidden,
            eventSink = ::handleEvent,
        )
    }

    private fun mode(
        updateState: UpdateState,
        settings: UpdateSettings,
        snooze: Snooze?,
        now: Long,
        isAllowedToInstall: Boolean,
        isMetered: Boolean,
        isFollowingUpdate: Boolean,
        autoInstallFailedVersionCode: Long,
    ): Mode {
        val candidate = updateState.candidate
        if (isFollowingUpdate && candidate != null) {
            val phase = when (updateState) {
                is UpdateState.Downloading -> Phase.Downloading(updateState.progress)
                is UpdateState.Verifying, is UpdateState.Installing -> Phase.Installing
                is UpdateState.AwaitingConfirmation -> Phase.AwaitingConfirmation
                is UpdateState.Failed -> return Mode.Failed(updateState.error)
                else -> Phase.Starting
            }
            return Mode.Progress(candidate.versionName, phase)
        }
        val offers = snooze != null && candidate != null && UpdatePolicy.shouldPrompt(
            state = updateState,
            settings = settings,
            snooze = snooze,
            nowMillis = now,
            isAllowedToInstall = isAllowedToInstall,
            isMetered = isMetered,
            autoInstallFailedVersionCode = autoInstallFailedVersionCode,
        )
        return if (offers) {
            Mode.Offer(
                versionName = candidate.versionName,
                releaseUrl = candidate.releaseUrl ?: RELEASES_URL,
                needsInstallPermission = !isAllowedToInstall,
                isMetered = isMetered,
            )
        } else {
            Mode.Hidden
        }
    }

    private companion object {
        const val RELEASES_URL = "https://github.com/c4g7-dev/InterCom/releases"
    }
}
