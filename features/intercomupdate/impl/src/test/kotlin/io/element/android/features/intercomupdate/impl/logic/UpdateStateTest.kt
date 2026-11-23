/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import com.google.common.truth.Truth.assertThat
import io.element.android.features.intercomupdate.impl.ui.anUpdateCandidate
import org.junit.Test

class UpdateStateTest {
    private val candidate = anUpdateCandidate()
    private val error = UpdateError.Network("offline")

    private fun UpdateState.after(vararg events: UpdateEvent) = events.fold(this) { state, event -> state.reduce(event) }

    @Test
    fun `a check ends up to date, with a release, or failed`() {
        val checking = UpdateState.Idle.reduce(UpdateEvent.CheckStarted)
        assertThat(checking).isEqualTo(UpdateState.Checking(null))
        assertThat(checking.reduce(UpdateEvent.NothingNewer)).isEqualTo(UpdateState.UpToDate)
        assertThat(checking.reduce(UpdateEvent.Found(candidate))).isEqualTo(UpdateState.Available(candidate))
        assertThat(checking.reduce(UpdateEvent.CheckFailed(error))).isEqualTo(UpdateState.Failed(error))
    }

    @Test
    fun `a failed check keeps the release that an earlier one found`() {
        val state = UpdateState.Available(candidate).after(UpdateEvent.CheckStarted, UpdateEvent.CheckFailed(error))
        assertThat(state).isEqualTo(UpdateState.Available(candidate))
    }

    @Test
    fun `an update goes from download over verification to the installer`() {
        var state: UpdateState = UpdateState.Available(candidate)
        state = state.reduce(UpdateEvent.DownloadStarted)
        assertThat(state).isEqualTo(UpdateState.Downloading(candidate, 0f))
        state = state.reduce(UpdateEvent.DownloadProgress(0.5f))
        assertThat(state).isEqualTo(UpdateState.Downloading(candidate, 0.5f))
        state = state.reduce(UpdateEvent.Downloaded)
        assertThat(state).isEqualTo(UpdateState.Verifying(candidate))
        state = state.reduce(UpdateEvent.Verified)
        assertThat(state).isEqualTo(UpdateState.Installing(candidate))
        state = state.reduce(UpdateEvent.ConfirmationRequested)
        assertThat(state).isEqualTo(UpdateState.AwaitingConfirmation(candidate))
    }

    @Test
    fun `a failure at any step keeps the release, and the update can be started again`() {
        val failure = UpdateError.ChecksumMismatch
        val busy = listOf(
            UpdateState.Downloading(candidate, 0.3f),
            UpdateState.Verifying(candidate),
            UpdateState.Installing(candidate),
            UpdateState.AwaitingConfirmation(candidate),
        )
        busy.forEach { state ->
            assertThat(state.isBusy).isTrue()
            val failed = state.reduce(UpdateEvent.UpdateFailed(failure))
            assertThat(failed).isEqualTo(UpdateState.Failed(failure, candidate))
            assertThat(failed.isBusy).isFalse()
            assertThat(failed.reduce(UpdateEvent.DownloadStarted)).isEqualTo(UpdateState.Downloading(candidate, 0f))
        }
    }

    @Test
    fun `the installer's refusal is shown even when the app was restarted meanwhile`() {
        val failure = UpdateError.Install(4, "INSTALL_FAILED_INSUFFICIENT_STORAGE")
        assertThat(UpdateState.Idle.reduce(UpdateEvent.UpdateFailed(failure))).isEqualTo(UpdateState.Failed(failure, null))
    }

    @Test
    fun `a postponed update is on offer again, but not once the installer has it`() {
        assertThat(UpdateState.Downloading(candidate, 1f).reduce(UpdateEvent.Postponed)).isEqualTo(UpdateState.Available(candidate))
        assertThat(UpdateState.Verifying(candidate).reduce(UpdateEvent.Postponed)).isEqualTo(UpdateState.Available(candidate))
        val installing = UpdateState.Installing(candidate)
        assertThat(installing.reduce(UpdateEvent.Postponed)).isSameInstanceAs(installing)
        assertThat(UpdateState.Idle.reduce(UpdateEvent.Postponed)).isSameInstanceAs(UpdateState.Idle)
    }

    @Test
    fun `an update under way is neither restarted nor replaced by a check`() {
        val downloading = UpdateState.Downloading(candidate, 0.3f)
        assertThat(downloading.reduce(UpdateEvent.DownloadStarted)).isSameInstanceAs(downloading)
        assertThat(downloading.reduce(UpdateEvent.CheckStarted)).isSameInstanceAs(downloading)
        assertThat(downloading.reduce(UpdateEvent.NothingNewer)).isSameInstanceAs(downloading)
        assertThat(downloading.reduce(UpdateEvent.Found(anUpdateCandidate(versionName = "26.11.1-intercom.6")))).isSameInstanceAs(downloading)
        assertThat(downloading.reduce(UpdateEvent.CheckFailed(error))).isSameInstanceAs(downloading)
    }

    @Test
    fun `events that do not belong to the state change nothing`() {
        val states = listOf(UpdateState.Idle, UpdateState.UpToDate, UpdateState.Failed(error))
        val events = listOf(
            UpdateEvent.DownloadStarted,
            UpdateEvent.DownloadProgress(0.1f),
            UpdateEvent.Downloaded,
            UpdateEvent.Verified,
            UpdateEvent.ConfirmationRequested,
            UpdateEvent.NothingNewer,
            UpdateEvent.Found(candidate),
        )
        states.forEach { state ->
            events.forEach { event -> assertThat(state.reduce(event)).isSameInstanceAs(state) }
        }
        // No download while a check runs, and no step is skipped.
        assertThat(UpdateState.Checking(candidate).reduce(UpdateEvent.DownloadStarted)).isEqualTo(UpdateState.Checking(candidate))
        assertThat(UpdateState.Downloading(candidate, 1f).reduce(UpdateEvent.Verified)).isEqualTo(UpdateState.Downloading(candidate, 1f))
        assertThat(UpdateState.Available(candidate).reduce(UpdateEvent.Downloaded)).isEqualTo(UpdateState.Available(candidate))
    }
}
