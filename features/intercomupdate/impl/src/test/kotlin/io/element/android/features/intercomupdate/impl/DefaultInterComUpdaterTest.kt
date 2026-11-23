/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import android.content.pm.PackageInstaller
import com.google.common.truth.Truth.assertThat
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.logic.A_SHA256
import io.element.android.features.intercomupdate.impl.logic.ApkInfo
import io.element.android.features.intercomupdate.impl.logic.ApkRejection
import io.element.android.features.intercomupdate.impl.logic.PACKAGE
import io.element.android.features.intercomupdate.impl.logic.ReleaseSelector
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import io.element.android.features.intercomupdate.impl.logic.UpdateState
import io.element.android.features.intercomupdate.impl.logic.aRelease
import io.element.android.features.intercomupdate.impl.logic.anAndroidRelease
import io.element.android.features.intercomupdate.impl.ui.anUpdateCandidate
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultInterComUpdaterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    // Most tests are about one step at a time: nothing installs itself unless a test turns that on.
    private val store = InMemoryInterComUpdateStore(initialSettings = UpdateSettings(autoInstall = false))
    private var isInCall = false
    private val source = FakeReleaseSource(
        releases = listOf(
            aRelease("v1.12.35-intercom.5"),
            anAndroidRelease(NEW),
            anAndroidRelease("26.11.1-intercom.3"),
        ),
        texts = mutableMapOf(MANIFEST_URL to manifestJson()),
        // Its SHA-256 is A_SHA256.
        apkContent = "abc".toByteArray(),
    )
    private val reader = FakeApkInfoReader(
        installed = ApkInfo(PACKAGE, 2_026_110_123, setOf("release-key")),
        archive = ApkInfo(PACKAGE, 2_026_110_124, setOf("release-key")),
    )
    private val installer = FakeApkInstaller()
    private val notifier = FakeUpdateNotifier()
    private val scheduler = FakeUpdateScheduler()
    private val clock = FakeSystemClock(epochMillisResult = 10_000_000)
    private lateinit var files: FakeUpdateFiles

    private fun TestScope.createUpdater(buildType: BuildType = BuildType.RELEASE): DefaultInterComUpdater {
        files = FakeUpdateFiles(temporaryFolder.root)
        return DefaultInterComUpdater(
            buildMeta = aBuildMeta(buildType = buildType, applicationId = PACKAGE, versionName = "26.11.1-intercom.3", versionCode = 2_026_110_123),
            store = store,
            releaseSource = source,
            apkInfoReader = reader,
            installer = installer,
            files = files,
            notifier = notifier,
            scheduler = scheduler,
            callMonitor = { isInCall },
            clock = clock,
            dispatchers = testCoroutineDispatchers(),
            coroutineScope = backgroundScope,
        )
    }

    @Test
    fun `an automatic check finds the release, remembers it and announces it once`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Background)
        val state = updater.state.value as UpdateState.Available
        assertThat(state.candidate.versionName).isEqualTo(NEW)
        assertThat(state.candidate.versionCode).isEqualTo(2_026_110_124L)
        assertThat(state.candidate.sha256).isEqualTo(A_SHA256)
        assertThat(store.candidate).isEqualTo(state.candidate)
        assertThat(store.lastCheck).isEqualTo(10_000_000)
        assertThat(notifier.events).containsExactly("available $NEW")
        updater.check(CheckTrigger.Background)
        assertThat(updater.state.value).isEqualTo(state)
        assertThat(notifier.events).hasSize(1)
    }

    @Test
    fun `a check with the app on screen shows no notification`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        assertThat(updater.state.value).isInstanceOf(UpdateState.Available::class.java)
        updater.check(CheckTrigger.AppOpen)
        assertThat(notifier.events).isEmpty()
    }

    @Test
    fun `opening the app checks, and not again within half an hour`() = runTest {
        val updater = createUpdater()
        updater.onAppOpened()
        assertThat(updater.state.value).isInstanceOf(UpdateState.Available::class.java)
        assertThat(store.lastCheckAttempt).isEqualTo(10_000_000)
        source.releases = emptyList()
        clock.epochMillisResult += 29 * 60_000
        updater.onAppOpened()
        assertThat(updater.state.value).isInstanceOf(UpdateState.Available::class.java)
        clock.epochMillisResult += 60_000
        updater.onAppOpened()
        assertThat(updater.state.value).isEqualTo(UpdateState.UpToDate)
    }

    @Test
    fun `a check that failed counts for the half hour too, and opening the app does not check with the checks off`() = runTest {
        val updater = createUpdater()
        source.failure = IOException("offline")
        updater.onAppOpened()
        assertThat(updater.state.value).isInstanceOf(UpdateState.Failed::class.java)
        source.failure = null
        clock.epochMillisResult += 60_000
        updater.onAppOpened()
        assertThat(updater.state.value).isInstanceOf(UpdateState.Failed::class.java)

        store.settings.value = UpdateSettings(checkAutomatically = false)
        clock.epochMillisResult += 3_600_000
        updater.onAppOpened()
        assertThat(updater.state.value).isInstanceOf(UpdateState.Failed::class.java)
    }

    @Test
    fun `with automatic installation a found release is downloaded at once, without a notification`() = runTest {
        store.settings.value = UpdateSettings(autoInstall = true)
        val updater = createUpdater()
        updater.check(CheckTrigger.Background)
        assertThat(scheduler.events).containsExactly("update auto")
        assertThat(notifier.events).isEmpty()
    }

    @Test
    fun `without the permission to install, a found release is offered instead, until the permission is there`() = runTest {
        store.settings.value = UpdateSettings(autoInstall = true)
        installer.isAllowed = false
        val updater = createUpdater()
        updater.check(CheckTrigger.Background)
        assertThat(scheduler.events).isEmpty()
        assertThat(notifier.events).containsExactly("available $NEW")
        installer.isAllowed = true
        updater.onAppOpened()
        assertThat(scheduler.events).containsExactly("update auto")
    }

    @Test
    fun `turning automatic installation on installs the release on offer`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        assertThat(scheduler.events).isEmpty()
        store.setSettings { it.copy(autoInstall = true) }
        updater.onSettingsChanged()
        assertThat(scheduler.events).containsExactly("periodic metered=false", "update auto").inOrder()
    }

    @Test
    fun `an automatic update waits for a call to end, one that was asked for does not`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        isInCall = true
        assertThat(updater.downloadAndInstall(userInitiated = false)).isEqualTo(UpdateRun.Postponed)
        assertThat(updater.state.value).isInstanceOf(UpdateState.Available::class.java)
        assertThat(source.downloads).isEqualTo(0)
        isInCall = false
        assertThat(updater.downloadAndInstall(userInitiated = false)).isEqualTo(UpdateRun.Done)
        assertThat(installer.installed).hasSize(1)
        updater.onInstallStatus(PackageInstaller.STATUS_FAILURE_ABORTED, null, null)
        isInCall = true
        assertThat(updater.downloadAndInstall(userInitiated = true)).isEqualTo(UpdateRun.Done)
        assertThat(installer.installed).hasSize(2)
    }

    @Test
    fun `a call that starts during the download postpones the installation and keeps the download`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        source.onDownload = { isInCall = true }
        assertThat(updater.downloadAndInstall(userInitiated = false)).isEqualTo(UpdateRun.Postponed)
        assertThat(updater.state.value).isInstanceOf(UpdateState.Available::class.java)
        assertThat(installer.installed).isEmpty()
        isInCall = false
        assertThat(updater.downloadAndInstall(userInitiated = false)).isEqualTo(UpdateRun.Done)
        assertThat(source.downloads).isEqualTo(1)
        assertThat(installer.installed).hasSize(1)
    }

    @Test
    fun `an automatic installation that failed is not tried again unasked, but a broken connection is`() = runTest {
        store.settings.value = UpdateSettings(autoInstall = true)
        val updater = createUpdater()
        updater.check(CheckTrigger.AppOpen)
        source.failure = IOException("connection reset")
        updater.downloadAndInstall(userInitiated = false)
        assertThat(store.autoInstallFailed).isEqualTo(0)
        source.failure = null
        reader.archive = ApkInfo(PACKAGE, 2_026_110_124, setOf("another-key"))
        updater.downloadAndInstall(userInitiated = false)
        assertThat(store.autoInstallFailed).isEqualTo(2_026_110_124L)
        scheduler.events.clear()
        updater.check(CheckTrigger.Background)
        assertThat(scheduler.events).isEmpty()
        assertThat(notifier.events).contains("available $NEW")
    }

    @Test
    fun `later snoozes the release on offer for a day`() = runTest {
        val updater = createUpdater()
        updater.snooze()
        assertThat(store.snooze.value.versionCode).isEqualTo(0)
        updater.check(CheckTrigger.AppOpen)
        updater.snooze()
        assertThat(store.snooze.value.versionCode).isEqualTo(2_026_110_124L)
        assertThat(store.snooze.value.untilMillis).isEqualTo(10_000_000 + 24 * 3_600_000L)
        // The release stays known: the settings keep their dot.
        assertThat(updater.state.value.candidate).isNotNull()
    }

    @Test
    fun `asking for the update schedules it on any connection, unless one is under way`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        updater.requestUpdate()
        runCurrent()
        assertThat(scheduler.events).containsExactly("update asked")
        updater.downloadAndInstall(userInitiated = true)
        updater.requestUpdate()
        runCurrent()
        assertThat(scheduler.events).hasSize(1)
    }

    @Test
    fun `the running version being the newest is up to date`() = runTest {
        reader.installed = ApkInfo(PACKAGE, 2_026_110_124, setOf("release-key"))
        store.candidate = anUpdateCandidate()
        val updater = createUpdater()
        updater.check(CheckTrigger.Background)
        assertThat(updater.state.value).isEqualTo(UpdateState.UpToDate)
        assertThat(store.candidate).isNull()
        assertThat(notifier.events).isEmpty()
    }

    @Test
    fun `a pre-release is offered only when the setting asks for it`() = runTest {
        source.releases = listOf(anAndroidRelease(NEW, prerelease = true))
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        assertThat(updater.state.value).isEqualTo(UpdateState.UpToDate)
        store.setSettings { it.copy(includePrereleases = true) }
        updater.check(CheckTrigger.Manual)
        assertThat(updater.state.value).isInstanceOf(UpdateState.Available::class.java)
    }

    @Test
    fun `a check without a network fails, and keeps a release that was found before`() = runTest {
        val updater = createUpdater()
        source.failure = IOException("offline")
        updater.check(CheckTrigger.Manual)
        assertThat(updater.state.value).isEqualTo(UpdateState.Failed(UpdateError.Network("offline")))
        source.failure = null
        updater.check(CheckTrigger.Manual)
        val available = updater.state.value
        source.failure = IOException("offline")
        updater.check(CheckTrigger.Manual)
        assertThat(updater.state.value).isEqualTo(available)
    }

    @Test
    fun `a manifest that contradicts the release is not offered`() = runTest {
        source.texts[MANIFEST_URL] = manifestJson(packageName = "io.element.android.x")
        val updater = createUpdater()
        updater.check(CheckTrigger.Background)
        assertThat((updater.state.value as UpdateState.Failed).error).isInstanceOf(UpdateError.BadRelease::class.java)
        source.texts[MANIFEST_URL] = "not json"
        updater.check(CheckTrigger.Background)
        assertThat((updater.state.value as UpdateState.Failed).error).isInstanceOf(UpdateError.BadRelease::class.java)
        assertThat(store.candidate).isNull()
        assertThat(notifier.events).isEmpty()
    }

    @Test
    fun `the update is downloaded, verified and handed to the installer`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        updater.downloadAndInstall(userInitiated = true)
        assertThat(updater.state.value).isInstanceOf(UpdateState.Installing::class.java)
        val (apk, candidate) = installer.installed.single()
        assertThat(apk.readText()).isEqualTo("abc")
        assertThat(candidate.versionName).isEqualTo(NEW)
        assertThat(source.downloads).isEqualTo(1)

        // Android asks, the user says no: the release stays on offer, and the file is not downloaded again.
        updater.onInstallStatus(PackageInstaller.STATUS_PENDING_USER_ACTION, null, null)
        assertThat(updater.state.value).isInstanceOf(UpdateState.AwaitingConfirmation::class.java)
        updater.onInstallStatus(PackageInstaller.STATUS_FAILURE_ABORTED, "INSTALL_FAILED_ABORTED: User rejected permissions", null)
        val failed = updater.state.value as UpdateState.Failed
        assertThat(failed.error).isEqualTo(UpdateError.Install(PackageInstaller.STATUS_FAILURE_ABORTED, "INSTALL_FAILED_ABORTED: User rejected permissions"))
        assertThat(failed.candidate).isEqualTo(candidate)
        assertThat(notifier.events).isEmpty()
        updater.downloadAndInstall(userInitiated = true)
        assertThat(source.downloads).isEqualTo(1)
        assertThat(installer.installed).hasSize(2)
    }

    @Test
    fun `an update started from the notification in a new process takes the release from the store`() = runTest {
        store.candidate = anUpdateCandidate(versionName = NEW, versionCode = 2_026_110_124).copy(sha256 = A_SHA256)
        val updater = createUpdater()
        updater.downloadAndInstall(userInitiated = true)
        assertThat(installer.installed).hasSize(1)
    }

    @Test
    fun `a download with another checksum is deleted and not installed`() = runTest {
        source.apkContent = "something else".toByteArray()
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        updater.downloadAndInstall(userInitiated = true)
        val failed = updater.state.value as UpdateState.Failed
        assertThat(failed.error).isEqualTo(UpdateError.ChecksumMismatch)
        assertThat(failed.candidate).isNotNull()
        assertThat(installer.installed).isEmpty()
        assertThat(temporaryFolder.root.listFiles()).isEmpty()
        assertThat(notifier.events).containsExactly("failed")
    }

    @Test
    fun `an APK signed with another key is deleted and not installed`() = runTest {
        reader.archive = ApkInfo(PACKAGE, 2_026_110_124, setOf("another-key"))
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        updater.downloadAndInstall(userInitiated = true)
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.BadApk(ApkRejection.SignatureMismatch))
        assertThat(installer.installed).isEmpty()
        assertThat(temporaryFolder.root.listFiles()).isEmpty()
    }

    @Test
    fun `an APK that Android cannot read, or of another version, is not installed`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        reader.archive = null
        updater.downloadAndInstall(userInitiated = true)
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.BadApk(ApkRejection.Unreadable))
        reader.archive = ApkInfo(PACKAGE, 2_026_110_129, setOf("release-key"))
        updater.downloadAndInstall(userInitiated = true)
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.BadApk(ApkRejection.WrongVersion))
        assertThat(installer.installed).isEmpty()
    }

    @Test
    fun `nothing is downloaded without the permission to install, or without the space`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        installer.isAllowed = false
        updater.downloadAndInstall(userInitiated = true)
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.InstallNotAllowed)
        installer.isAllowed = true
        files.freeBytes = 5
        updater.downloadAndInstall(userInitiated = true)
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.NoSpace)
        assertThat(source.downloads).isEqualTo(0)
        assertThat(installer.installed).isEmpty()
    }

    @Test
    fun `a download that breaks off fails with the release kept`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        source.failure = IOException("connection reset")
        updater.downloadAndInstall(userInitiated = true)
        val failed = updater.state.value as UpdateState.Failed
        assertThat(failed.error).isEqualTo(UpdateError.Network("connection reset"))
        assertThat(failed.candidate).isNotNull()
    }

    @Test
    fun `the installer's refusal is kept with its status and message`() = runTest {
        val updater = createUpdater()
        updater.check(CheckTrigger.Manual)
        updater.downloadAndInstall(userInitiated = true)
        updater.onInstallStatus(PackageInstaller.STATUS_FAILURE_STORAGE, "INSTALL_FAILED_INSUFFICIENT_STORAGE", null)
        val failed = updater.state.value as UpdateState.Failed
        assertThat(failed.error).isEqualTo(UpdateError.Install(PackageInstaller.STATUS_FAILURE_STORAGE, "INSTALL_FAILED_INSUFFICIENT_STORAGE"))
        assertThat(notifier.events).containsExactly("failed")
    }

    @Test
    fun `the start offers a remembered release again and schedules the checks`() = runTest {
        val candidate = anUpdateCandidate(versionName = NEW, versionCode = 2_026_110_124)
        store.candidate = candidate
        val updater = createUpdater()
        updater.start()
        assertThat(updater.state.value).isEqualTo(UpdateState.Available(candidate))
        assertThat(scheduler.events).containsExactly("periodic metered=false")
    }

    @Test
    fun `the start forgets a release that is installed by now`() = runTest {
        store.candidate = anUpdateCandidate(versionName = "26.11.1-intercom.3", versionCode = 2_026_110_123)
        File(temporaryFolder.root, "old.apk").writeText("old")
        val updater = createUpdater()
        updater.start()
        assertThat(updater.state.value).isEqualTo(UpdateState.Idle)
        assertThat(store.candidate).isNull()
        assertThat(temporaryFolder.root.listFiles()).isEmpty()
        assertThat(scheduler.events).containsExactly("periodic metered=false")
    }

    @Test
    fun `with the automatic checks off nothing is scheduled but the cancellation`() = runTest {
        store.settings.value = UpdateSettings(checkAutomatically = false)
        val updater = createUpdater()
        updater.start()
        updater.onAppOpened()
        assertThat(scheduler.events).containsExactly("no periodic")
    }

    @Test
    fun `the new version says so after it replaced the old one`() = runTest {
        store.candidate = anUpdateCandidate()
        val updater = createUpdater()
        updater.onAppReplaced()
        runCurrent()
        assertThat(store.candidate).isNull()
        assertThat(notifier.events).containsExactly("updated 26.11.1-intercom.3")
    }

    @Test
    fun `a debug build does not look for updates`() = runTest {
        val updater = createUpdater(buildType = BuildType.DEBUG)
        updater.start()
        updater.onAppOpened()
        updater.check(CheckTrigger.Manual)
        updater.downloadAndInstall(userInitiated = true)
        updater.requestUpdate()
        runCurrent()
        assertThat(updater.isSupported).isFalse()
        assertThat(updater.state.value).isEqualTo(UpdateState.Idle)
        assertThat(scheduler.events).isEmpty()
        assertThat(source.downloads).isEqualTo(0)
    }

    private fun manifestJson(packageName: String = PACKAGE) = """
        {
          "schema": 1,
          "package": "$packageName",
          "versionCode": 2026110124,
          "versionName": "$NEW",
          "abi": "arm64-v8a",
          "minSdk": 24,
          "apk": "InterCom-Android-$NEW-arm64-v8a.apk",
          "size": 3,
          "sha256": "$A_SHA256",
          "notes": "Notes",
          "a field of a later version": true
        }
    """.trimIndent()

    private companion object {
        const val NEW = "26.11.1-intercom.4"
        const val MANIFEST_URL = "${ReleaseSelector.DOWNLOAD_PREFIX}android-v$NEW/android-update.json"
    }
}
