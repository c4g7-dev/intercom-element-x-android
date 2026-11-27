/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.callnative.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * InterCom: tells the user that a call was left for them because they were alone in it. The other person left a
 * call in a direct chat, nobody came back within the time the call waits, and the call screen has closed by then,
 * so it is said here, over whatever the app shows next, until it is dismissed.
 */
interface NativeCallAloneNotice {
    /** Draws nothing while there is nothing to say. */
    @Composable
    fun Render(modifier: Modifier)
}
