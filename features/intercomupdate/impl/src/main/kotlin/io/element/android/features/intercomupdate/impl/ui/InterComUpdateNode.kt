/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.intercomupdate.impl.install.openInstallPermissionSettings
import io.element.android.features.intercomupdate.impl.install.openSettingsFor

@ContributesNode(AppScope::class)
@AssistedInject
class InterComUpdateNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: InterComUpdatePresenter,
) : Node(
    buildContext = buildContext,
    plugins = plugins
) {
    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val context = LocalContext.current
        InterComUpdateView(
            state = state,
            onBackClick = ::navigateUp,
            onAllowInstallClick = { context.openInstallPermissionSettings() },
            onOpenHintSettings = { hint -> context.openSettingsFor(hint) },
            modifier = modifier,
        )
    }
}
