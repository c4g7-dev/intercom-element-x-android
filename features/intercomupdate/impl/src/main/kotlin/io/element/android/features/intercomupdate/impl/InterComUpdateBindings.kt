/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/** For the receiver, which Android creates. */
@ContributesTo(AppScope::class)
interface InterComUpdateBindings {
    fun interComUpdater(): InterComUpdater
}
