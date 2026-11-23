/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.intercomupdate.impl.InterComUpdater
import io.element.android.features.intercomupdate.impl.data.InterComUpdateStore
import io.element.android.features.intercomupdate.impl.data.NetworkMeter
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.install.ApkInstaller
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.di.annotations.AppCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Inject
class InterComUpdatePresenter(
    private val updater: InterComUpdater,
    private val store: InterComUpdateStore,
    private val installer: ApkInstaller,
    private val networkMeter: NetworkMeter,
    private val buildMeta: BuildMeta,
    // A check or a change of the settings is finished even when the screen is left meanwhile.
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : Presenter<InterComUpdateState> {
    @Composable
    override fun present(): InterComUpdateState {
        val settings by remember { store.settings }.collectAsState(initial = UpdateSettings())
        val updateState by updater.state.collectAsState()
        var isAllowedToInstall by remember { mutableStateOf(installer.isAllowedToInstall()) }
        var isMetered by remember { mutableStateOf(networkMeter.isMetered()) }

        fun changeSettings(transform: (UpdateSettings) -> UpdateSettings) = appCoroutineScope.launch {
            store.setSettings(transform)
            updater.onSettingsChanged()
        }

        fun handleEvent(event: InterComUpdateEvent) {
            when (event) {
                is InterComUpdateEvent.SetCheckAutomatically -> changeSettings { it.copy(checkAutomatically = event.enabled) }
                is InterComUpdateEvent.SetAllowMetered -> changeSettings { it.copy(allowMetered = event.enabled) }
                is InterComUpdateEvent.SetIncludePrereleases -> changeSettings { it.copy(includePrereleases = event.enabled) }
                InterComUpdateEvent.CheckNow -> {
                    isMetered = networkMeter.isMetered()
                    appCoroutineScope.launch { updater.check(manual = true) }
                }
                InterComUpdateEvent.Update -> {
                    isMetered = networkMeter.isMetered()
                    updater.requestUpdate()
                }
                InterComUpdateEvent.Resumed -> {
                    isAllowedToInstall = installer.isAllowedToInstall()
                    isMetered = networkMeter.isMetered()
                }
            }
        }

        return InterComUpdateState(
            isSupported = updater.isSupported,
            installedVersion = buildMeta.versionName,
            settings = settings,
            updateState = updateState,
            isAllowedToInstall = isAllowedToInstall,
            isMetered = isMetered,
            eventSink = ::handleEvent,
        )
    }
}
