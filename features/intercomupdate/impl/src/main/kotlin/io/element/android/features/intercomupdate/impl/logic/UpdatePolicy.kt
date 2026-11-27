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
import kotlin.time.Duration.Companion.seconds

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

    /** The feed is looked at when the app comes to the screen or the network returns, but not more often than this. */
    val FEED_LOOK_MIN_GAP = 1.minutes

    /** While the app stays on screen, the feed is looked at this often. */
    val FOREGROUND_LOOK_INTERVAL = 30.minutes

    /** The releases are asked for after this long whatever the feed says. */
    val API_MAX_AGE = 6.hours

    /** Never two automatic requests to the API closer than this: it may answer from a copy that is a minute old. */
    val API_MIN_GAP = 90.seconds

    /** After a request to the API that failed, the wait before the next automatic one. */
    val API_GAP_AFTER_FAILURE = 15.minutes

    /** Whether the feed is looked at now, the last look having started at [lastLookMillis] (0 for never). */
    fun shouldLookAtFeed(settings: UpdateSettings, nowMillis: Long, lastLookMillis: Long): Boolean {
        if (!settings.checkAutomatically) return false
        val elapsed = nowMillis - lastLookMillis
        return lastLookMillis <= 0 || elapsed < 0 || elapsed >= FEED_LOOK_MIN_GAP.inWholeMilliseconds
    }

    /**
     * Whether the releases themselves are asked for, from the GitHub API, after the feed gave [answer]. The API
     * allows an address without a login 60 requests an hour and counts unchanged answers too, so it is asked only
     * when the feed's Android releases differ from those in [memory], once more after a newer release in the feed
     * was not in the API's answer yet, and every [API_MAX_AGE]. [lastCheckMillis] is the last successful request,
     * [lastAttemptMillis] the start of the last one, successful or not.
     */
    fun shouldAskApi(
        answer: FeedAnswer,
        memory: FeedMemory,
        settings: UpdateSettings,
        nowMillis: Long,
        lastCheckMillis: Long,
        lastAttemptMillis: Long,
    ): Boolean {
        if (!settings.checkAutomatically) return false
        val sinceAttempt = nowMillis - lastAttemptMillis
        val minGap = if (lastAttemptMillis > lastCheckMillis) API_GAP_AFTER_FAILURE else API_MIN_GAP
        if (lastAttemptMillis > 0 && sinceAttempt >= 0 && sinceAttempt < minGap.inWholeMilliseconds) return false
        val sinceCheck = nowMillis - lastCheckMillis
        val isStale = lastCheckMillis <= 0 || sinceCheck < 0 || sinceCheck >= API_MAX_AGE.inWholeMilliseconds
        return when (answer) {
            // Without the feed the app looks as it did before it read the feed.
            FeedAnswer.Failed -> shouldCheckOnOpen(settings, nowMillis, lastAttemptMillis)
            FeedAnswer.NotModified -> memory.retryPending || isStale
            is FeedAnswer.Changed -> answer.fingerprint != memory.fingerprint || memory.retryPending || isStale
        }
    }

    /**
     * What to remember of the feed after the API was asked because of [answer] and found [foundNewer] or not.
     * A release that the feed shows as newer than the running app ([currentVersionCode]) and the API did not
     * offer is asked for once more, and then taken as one this app is not offered (a pre-release, say).
     */
    fun feedMemoryAfterCheck(answer: FeedAnswer, memory: FeedMemory, foundNewer: Boolean, currentVersionCode: Long): FeedMemory {
        return when (answer) {
            FeedAnswer.Failed -> memory
            FeedAnswer.NotModified -> memory.copy(retryPending = false)
            is FeedAnswer.Changed -> FeedMemory(
                etag = answer.etag,
                fingerprint = answer.fingerprint,
                retryPending = !foundNewer && answer.newestVersionCode > currentVersionCode && answer.fingerprint != memory.fingerprint,
            )
        }
    }

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
