/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.prompt

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.intercomupdate.api.InterComUpdatePrompt
import io.element.android.features.intercomupdate.impl.install.openInstallPermissionSettings
import io.element.android.libraries.core.extensions.runCatchingExceptions

@ContributesBinding(AppScope::class)
class DefaultInterComUpdatePrompt(
    private val presenter: InterComUpdatePromptPresenter,
) : InterComUpdatePrompt {
    @Composable
    override fun Render(modifier: Modifier) {
        val state = presenter.present()
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        InterComUpdatePromptView(
            state = state,
            // Without a browser there is nowhere to open the page.
            onOpenReleaseNotes = { url -> runCatchingExceptions { uriHandler.openUri(url) } },
            onAllowInstallClick = { context.openInstallPermissionSettings() },
            modifier = modifier,
        )
    }
}
