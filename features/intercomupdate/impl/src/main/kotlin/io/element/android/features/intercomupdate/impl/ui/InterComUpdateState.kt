/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.ui

import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.install.InstallHint
import io.element.android.features.intercomupdate.impl.logic.UpdateState

data class InterComUpdateState(
    val isSupported: Boolean,
    val installedVersion: String,
    val settings: UpdateSettings,
    val updateState: UpdateState,
    /** Whether the user has allowed InterCom to install apps. */
    val isAllowedToInstall: Boolean,
    /** Whether a download would wait, because the connection is metered and mobile data is not allowed. */
    val isMetered: Boolean,
    /** What the user can do about an installation that failed, when that is more than trying again. */
    val installHint: InstallHint?,
    val eventSink: (InterComUpdateEvent) -> Unit,
)
