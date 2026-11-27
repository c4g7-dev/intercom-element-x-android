/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.prompt

import android.content.pm.PackageInstaller
import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.intercomupdate.impl.FakeApkInstaller
import io.element.android.features.intercomupdate.impl.FakeDeviceInfo
import io.element.android.features.intercomupdate.impl.FakeInterComUpdater
import io.element.android.features.intercomupdate.impl.InMemoryInterComUpdateStore
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.install.InstallHint
import io.element.android.features.intercomupdate.impl.logic.ReleaseNote
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import io.element.android.features.intercomupdate.impl.logic.UpdateState
import io.element.android.features.intercomupdate.impl.logic.WhatsNew
import io.element.android.features.intercomupdate.impl.prompt.InterComUpdatePromptState.Mode
import io.element.android.features.intercomupdate.impl.ui.anUpdateCandidate
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterComUpdatePromptPresenterTest {
    private val store = InMemoryInterComUpdateStore(initialSettings = UpdateSettings(autoInstall = false))
    private val notes = listOf(
        ReleaseNote("26.11.1-intercom.5", 2_026_110_125, "- five"),
        ReleaseNote("26.11.1-intercom.4", 2_026_110_124, "- four"),
    )

    private fun TestScope.createInterComUpdatePromptPresenter(
        updater: FakeInterComUpdater,
        manufacturer: String = "Google",
    ) = InterComUpdatePromptPresenter(
        updater = updater,
        store = store,
        installer = FakeApkInstaller(),
        networkMeter = { false },
        clock = FakeSystemClock(),
        deviceInfo = FakeDeviceInfo(manufacturer = manufacturer),
        appCoroutineScope = backgroundScope,
    )

    private suspend fun ReceiveTurbine<InterComUpdatePromptState>.awaitMode(predicate: (Mode) -> Boolean): InterComUpdatePromptState {
        while (true) {
            val item = awaitItem()
            if (predicate(item.mode)) return item
        }
    }

    @Test
    fun `an update on offer comes with what is new in it and in the releases it skips`() = runTest {
        val updater = FakeInterComUpdater(UpdateState.Available(anUpdateCandidate(notesHistory = notes)))
        createInterComUpdatePromptPresenter(updater).test {
            val offer = awaitMode { it is Mode.Offer }.mode as Mode.Offer
            assertThat(offer.notes).containsExactlyElementsIn(notes).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `what's new is shown after an update until it is closed, and not with the setting off`() = runTest {
        store.whatsNew.value = WhatsNew("26.11.1-intercom.5", 2_026_110_125, notes)
        val updater = FakeInterComUpdater(UpdateState.UpToDate)
        createInterComUpdatePromptPresenter(updater).test {
            val state = awaitMode { it is Mode.WhatsNew }
            assertThat((state.mode as Mode.WhatsNew).versionName).isEqualTo("26.11.1-intercom.5")
            state.eventSink(InterComUpdatePromptEvent.DismissWhatsNew)
            runCurrent()
            assertThat(updater.calls).containsExactly("dismissWhatsNew")
            store.setSettings { it.copy(showWhatsNew = false) }
            awaitMode { it == Mode.Hidden }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an update on offer comes before what's new`() = runTest {
        store.whatsNew.value = WhatsNew("26.11.1-intercom.5", 2_026_110_125, notes)
        val updater = FakeInterComUpdater(UpdateState.Available(anUpdateCandidate(versionName = "26.11.1-intercom.6", versionCode = 2_026_110_126)))
        createInterComUpdatePromptPresenter(updater).test {
            awaitMode { it is Mode.Offer }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `on a Samsung phone an installation that Android refused is brought up with the Auto Blocker hint`() = runTest {
        val failed = UpdateState.Failed(UpdateError.Install(PackageInstaller.STATUS_FAILURE_BLOCKED, null), anUpdateCandidate())
        val updater = FakeInterComUpdater(failed)
        createInterComUpdatePromptPresenter(updater, manufacturer = "samsung").test {
            val state = awaitMode { it is Mode.Failed }
            assertThat((state.mode as Mode.Failed).hint).isEqualTo(InstallHint.SamsungAutoBlocker)
            // Closed, it is not brought up again.
            state.eventSink(InterComUpdatePromptEvent.Hide)
            awaitMode { it == Mode.Hidden }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `on another phone a refused automatic installation stays out of the way`() = runTest {
        val failed = UpdateState.Failed(UpdateError.Install(PackageInstaller.STATUS_FAILURE_BLOCKED, null), anUpdateCandidate())
        createInterComUpdatePromptPresenter(FakeInterComUpdater(failed)).test {
            assertThat(awaitItem().mode).isEqualTo(Mode.Hidden)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back in the app ten seconds after Android asked to confirm, the update counts as not installed`() = runTest {
        val updater = FakeInterComUpdater(UpdateState.AwaitingConfirmation(anUpdateCandidate()))
        createInterComUpdatePromptPresenter(updater, manufacturer = "samsung").test {
            val state = awaitItem()
            state.eventSink(InterComUpdatePromptEvent.Resumed)
            advanceTimeBy(9_000)
            assertThat(updater.calls).isEmpty()
            advanceTimeBy(2_000)
            val failed = awaitMode { it is Mode.Failed }.mode as Mode.Failed
            assertThat(updater.calls).containsExactly("onConfirmationAbandoned")
            assertThat(failed.error).isEqualTo(UpdateError.NotConfirmed)
            assertThat(failed.hint).isEqualTo(InstallHint.SamsungAutoBlocker)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
