/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.intercomupdate.api.InterComUpdateIndicator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
class DefaultInterComUpdateIndicator(
    updater: InterComUpdater,
) : InterComUpdateIndicator {
    override val availableVersion: Flow<String?> = updater.state.map { it.candidate?.versionName }.distinctUntilChanged()
    override val isUpdateAvailable: Flow<Boolean> = availableVersion.map { it != null }.distinctUntilChanged()
}
