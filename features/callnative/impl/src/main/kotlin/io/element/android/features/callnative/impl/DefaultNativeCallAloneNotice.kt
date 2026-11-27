/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.callnative.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.callnative.api.NativeCallAloneNotice
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Kept for the life of the process, not of a screen: the call ends while the phone may lie on a table, and the
 * notice has to be there when the app is looked at again.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultNativeCallAloneNotice : NativeCallAloneNotice {
    private val _isPending = MutableStateFlow(false)
    val isPending: StateFlow<Boolean> = _isPending.asStateFlow()

    /** A call was left because the user was alone in it. */
    fun onDisconnectedAlone() {
        _isPending.value = true
    }

    fun dismiss() {
        _isPending.value = false
    }

    @Composable
    override fun Render(modifier: Modifier) {
        val isPending by isPending.collectAsState()
        if (isPending) {
            // Not translated, as the rest of the fork's own texts.
            ErrorDialog(
                modifier = modifier,
                title = TITLE,
                content = MESSAGE,
                onSubmit = ::dismiss,
            )
        }
    }

    companion object {
        /** How long a call in a direct chat goes on once the other person has left it, as on the desktop. */
        val ALONE_TIMEOUT: Duration = 5.minutes

        const val TITLE = "Call ended"
        const val MESSAGE = "You were disconnected because you were alone in the call."
    }
}
