/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import io.element.android.features.intercomupdate.impl.data.Snooze
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** What to do with a release that was found. */
enum class OnFound {
    /** Download it now and install it as soon as possible, without asking. */
    AutoInstall,

    /** Offer it: the dialog when the app is open, the notification when it is not. */
    Offer,
}

/** The updater's decisions that are not about one update's progress: when to look, when to ask, when not to. */
object UpdatePolicy {
    /** At most one check for this long, however often the app is opened. */
    val OPEN_CHECK_INTERVAL = 30.minutes

    /** How long "Later" keeps the dialog away for the release it was said to. */
    val SNOOZE_DURATION = 24.hours

    /**
     * Whether opening the app at [nowMillis] looks for an update, the last look, successful or not, having started
     * at [lastAttemptMillis] (0 for never).
     */
    fun shouldCheckOnOpen(settings: UpdateSettings, nowMillis: Long, lastAttemptMillis: Long): Boolean {
        if (!settings.checkAutomatically) return false
        val elapsed = nowMillis - lastAttemptMillis
        // A last attempt in the future is a clock that was set back, not a reason to wait for it.
        return lastAttemptMillis <= 0 || elapsed < 0 || elapsed >= OPEN_CHECK_INTERVAL.inWholeMilliseconds
    }

    fun snoozeFor(versionCode: Long, nowMillis: Long) = Snooze(versionCode, nowMillis + SNOOZE_DURATION.inWholeMilliseconds)

    /** A snooze holds for the one release it was said to; a newer release is offered at once. */
    fun isSnoozed(snooze: Snooze, versionCode: Long, nowMillis: Long): Boolean {
        return snooze.versionCode == versionCode && nowMillis < snooze.untilMillis
    }

    /**
     * [isAllowedToInstall]: whether the user has allowed InterCom to install apps, without which nothing can be
     * installed unasked. [autoInstallFailedVersionCode]: the release whose automatic installation failed before, for a
     * reason that trying again would not change.
     */
    fun onFound(
        settings: UpdateSettings,
        versionCode: Long,
        isAllowedToInstall: Boolean,
        autoInstallFailedVersionCode: Long,
    ): OnFound {
        return if (settings.autoInstall && isAllowedToInstall && autoInstallFailedVersionCode != versionCode) OnFound.AutoInstall else OnFound.Offer
    }

    /**
     * Whether the "is available" dialog is shown when the app is opened: for a release that is on offer and not
     * snoozed, unless it is being installed automatically anyway. An automatic download that waits for Wi-Fi does
     * not count as that, so the dialog offers to download now.
     */
    fun shouldPrompt(
        state: UpdateState,
        settings: UpdateSettings,
        snooze: Snooze,
        nowMillis: Long,
        isAllowedToInstall: Boolean,
        isMetered: Boolean,
        autoInstallFailedVersionCode: Long,
    ): Boolean {
        val candidate = (state as? UpdateState.Available)?.candidate ?: return false
        if (isSnoozed(snooze, candidate.versionCode, nowMillis)) return false
        val installsItself = onFound(settings, candidate.versionCode, isAllowedToInstall, autoInstallFailedVersionCode) == OnFound.AutoInstall &&
            !(isMetered && !settings.allowMetered)
        return !installsItself
    }

    /** A download the user asked for uses whatever connection there is; an automatic one follows the setting. */
    fun needsUnmetered(settings: UpdateSettings, userInitiated: Boolean): Boolean = !userInitiated && !settings.allowMetered
}
