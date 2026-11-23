/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** InterCom: the dialog that offers a newer release when the app is opened, and shows the update's progress. */
interface InterComUpdatePrompt {
    /** Draws nothing while there is nothing to offer. */
    @Composable
    fun Render(modifier: Modifier)
}
