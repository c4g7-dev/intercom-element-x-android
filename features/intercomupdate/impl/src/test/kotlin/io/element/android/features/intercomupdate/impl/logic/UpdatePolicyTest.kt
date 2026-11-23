/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import com.google.common.truth.Truth.assertThat
import io.element.android.features.intercomupdate.impl.data.Snooze
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.ui.anUpdateCandidate
import org.junit.Test

class UpdatePolicyTest {
    private val settings = UpdateSettings()
    private val candidate = anUpdateCandidate()
    private val available = UpdateState.Available(candidate)
    private val now = 1_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `opening the app checks the first time and then at most every half hour`() {
        assertThat(UpdatePolicy.shouldCheckOnOpen(settings, now, lastAttemptMillis = 0)).isTrue()
        assertThat(UpdatePolicy.shouldCheckOnOpen(settings, now, now)).isFalse()
        assertThat(UpdatePolicy.shouldCheckOnOpen(settings, now, now - minute)).isFalse()
        assertThat(UpdatePolicy.shouldCheckOnOpen(settings, now, now - 30 * minute + 1)).isFalse()
        assertThat(UpdatePolicy.shouldCheckOnOpen(settings, now, now - 30 * minute)).isTrue()
        assertThat(UpdatePolicy.shouldCheckOnOpen(settings, now, now - 5 * hour)).isTrue()
    }

    @Test
    fun `a clock that was set back does not hold the check up`() {
        assertThat(UpdatePolicy.shouldCheckOnOpen(settings, now, now + 5 * hour)).isTrue()
    }

    @Test
    fun `opening the app checks on any connection, but not with the checks switched off`() {
        // The kind of connection is no input: "Use mobile data" is about downloads.
        assertThat(UpdatePolicy.shouldCheckOnOpen(settings.copy(allowMetered = false), now, 0)).isTrue()
        assertThat(UpdatePolicy.shouldCheckOnOpen(settings.copy(checkAutomatically = false), now, 0)).isFalse()
    }

    @Test
    fun `later holds for a day, for that release only`() {
        val snooze = UpdatePolicy.snoozeFor(candidate.versionCode, now)
        assertThat(snooze).isEqualTo(Snooze(candidate.versionCode, now + 24 * hour))
        assertThat(UpdatePolicy.isSnoozed(snooze, candidate.versionCode, now)).isTrue()
        assertThat(UpdatePolicy.isSnoozed(snooze, candidate.versionCode, now + 24 * hour - 1)).isTrue()
        assertThat(UpdatePolicy.isSnoozed(snooze, candidate.versionCode, now + 24 * hour)).isFalse()
        assertThat(UpdatePolicy.isSnoozed(snooze, candidate.versionCode + 1, now)).isFalse()
        assertThat(UpdatePolicy.isSnoozed(Snooze(), candidate.versionCode, now)).isFalse()
    }

    @Test
    fun `a found release installs itself when that is on, allowed, and has not failed before`() {
        val code = candidate.versionCode
        assertThat(UpdatePolicy.onFound(settings, code, isAllowedToInstall = true, autoInstallFailedVersionCode = 0)).isEqualTo(OnFound.AutoInstall)
        assertThat(UpdatePolicy.onFound(settings.copy(autoInstall = false), code, true, 0)).isEqualTo(OnFound.Offer)
        // The first update: Android has not been asked yet whether InterCom may install apps.
        assertThat(UpdatePolicy.onFound(settings, code, isAllowedToInstall = false, autoInstallFailedVersionCode = 0)).isEqualTo(OnFound.Offer)
        assertThat(UpdatePolicy.onFound(settings, code, true, autoInstallFailedVersionCode = code)).isEqualTo(OnFound.Offer)
        // The failure of an older release says nothing about this one.
        assertThat(UpdatePolicy.onFound(settings, code, true, autoInstallFailedVersionCode = code - 1)).isEqualTo(OnFound.AutoInstall)
    }

    @Test
    fun `automatic installation is on by default`() {
        assertThat(UpdateSettings().autoInstall).isTrue()
    }

    @Test
    fun `an automatic download follows the connection setting, one that was asked for does not`() {
        assertThat(UpdatePolicy.needsUnmetered(settings, userInitiated = false)).isTrue()
        assertThat(UpdatePolicy.needsUnmetered(settings.copy(allowMetered = true), userInitiated = false)).isFalse()
        assertThat(UpdatePolicy.needsUnmetered(settings, userInitiated = true)).isFalse()
    }

    private fun shouldPrompt(
        state: UpdateState = available,
        settings: UpdateSettings = this.settings.copy(autoInstall = false),
        snooze: Snooze = Snooze(),
        nowMillis: Long = now,
        isAllowedToInstall: Boolean = true,
        isMetered: Boolean = false,
        autoInstallFailedVersionCode: Long = 0,
    ) = UpdatePolicy.shouldPrompt(state, settings, snooze, nowMillis, isAllowedToInstall, isMetered, autoInstallFailedVersionCode)

    @Test
    fun `the dialog offers a release that is available and not snoozed`() {
        assertThat(shouldPrompt()).isTrue()
        val snooze = UpdatePolicy.snoozeFor(candidate.versionCode, now)
        assertThat(shouldPrompt(snooze = snooze)).isFalse()
        assertThat(shouldPrompt(snooze = snooze, nowMillis = now + 25 * hour)).isTrue()
        // A newer release than the snoozed one is offered at once.
        val newer = UpdateState.Available(anUpdateCandidate(versionCode = candidate.versionCode + 1))
        assertThat(shouldPrompt(state = newer, snooze = snooze)).isTrue()
    }

    @Test
    fun `the dialog offers nothing when there is nothing, or an update is under way`() {
        val states = listOf(
            UpdateState.Idle,
            UpdateState.UpToDate,
            UpdateState.Checking(candidate),
            UpdateState.Downloading(candidate, 0.5f),
            UpdateState.Installing(candidate),
            UpdateState.Failed(UpdateError.NoSpace, candidate),
        )
        states.forEach { assertThat(shouldPrompt(state = it)).isFalse() }
    }

    @Test
    fun `no dialog for a release that installs itself, unless it cannot yet`() {
        val auto = settings.copy(autoInstall = true)
        assertThat(shouldPrompt(settings = auto)).isFalse()
        // Not allowed to install yet: one tap is needed.
        assertThat(shouldPrompt(settings = auto, isAllowedToInstall = false)).isTrue()
        // The automatic download waits for Wi-Fi: the dialog offers to download now.
        assertThat(shouldPrompt(settings = auto, isMetered = true)).isTrue()
        assertThat(shouldPrompt(settings = auto.copy(allowMetered = true), isMetered = true)).isFalse()
        // It was tried and failed.
        assertThat(shouldPrompt(settings = auto, autoInstallFailedVersionCode = candidate.versionCode)).isTrue()
        // And "Later" holds for all of these.
        val snooze = UpdatePolicy.snoozeFor(candidate.versionCode, now)
        assertThat(shouldPrompt(settings = auto, isAllowedToInstall = false, snooze = snooze)).isFalse()
    }
}
