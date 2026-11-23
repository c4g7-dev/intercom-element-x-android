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

    private val store = InMemoryInterComUpdateStore()
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
            clock = clock,
            dispatchers = testCoroutineDispatchers(),
            coroutineScope = backgroundScope,
        )
    }

    @Test
    fun `an automatic check finds the release, remembers it and announces it once`() = runTest {
        val updater = createUpdater()
        updater.check(manual = false)
        val state = updater.state.value as UpdateState.Available
        assertThat(state.candidate.versionName).isEqualTo(NEW)
        assertThat(state.candidate.versionCode).isEqualTo(2_026_110_124L)
        assertThat(state.candidate.sha256).isEqualTo(A_SHA256)
        assertThat(store.candidate).isEqualTo(state.candidate)
        assertThat(store.lastCheck).isEqualTo(10_000_000)
        assertThat(notifier.events).containsExactly("available $NEW")
        updater.check(manual = false)
        assertThat(updater.state.value).isEqualTo(state)
        assertThat(notifier.events).hasSize(1)
    }

    @Test
    fun `a manual check shows no notification`() = runTest {
        val updater = createUpdater()
        updater.check(manual = true)
        assertThat(updater.state.value).isInstanceOf(UpdateState.Available::class.java)
        assertThat(notifier.events).isEmpty()
    }

    @Test
    fun `the running version being the newest is up to date`() = runTest {
        reader.installed = ApkInfo(PACKAGE, 2_026_110_124, setOf("release-key"))
        store.candidate = anUpdateCandidate()
        val updater = createUpdater()
        updater.check(manual = false)
        assertThat(updater.state.value).isEqualTo(UpdateState.UpToDate)
        assertThat(store.candidate).isNull()
        assertThat(notifier.events).isEmpty()
    }

    @Test
    fun `a pre-release is offered only when the setting asks for it`() = runTest {
        source.releases = listOf(anAndroidRelease(NEW, prerelease = true))
        val updater = createUpdater()
        updater.check(manual = true)
        assertThat(updater.state.value).isEqualTo(UpdateState.UpToDate)
        store.setSettings { it.copy(includePrereleases = true) }
        updater.check(manual = true)
        assertThat(updater.state.value).isInstanceOf(UpdateState.Available::class.java)
    }

    @Test
    fun `a check without a network fails, and keeps a release that was found before`() = runTest {
        val updater = createUpdater()
        source.failure = IOException("offline")
        updater.check(manual = true)
        assertThat(updater.state.value).isEqualTo(UpdateState.Failed(UpdateError.Network("offline")))
        source.failure = null
        updater.check(manual = true)
        val available = updater.state.value
        source.failure = IOException("offline")
        updater.check(manual = true)
        assertThat(updater.state.value).isEqualTo(available)
    }

    @Test
    fun `a manifest that contradicts the release is not offered`() = runTest {
        source.texts[MANIFEST_URL] = manifestJson(packageName = "io.element.android.x")
        val updater = createUpdater()
        updater.check(manual = false)
        assertThat((updater.state.value as UpdateState.Failed).error).isInstanceOf(UpdateError.BadRelease::class.java)
        source.texts[MANIFEST_URL] = "not json"
        updater.check(manual = false)
        assertThat((updater.state.value as UpdateState.Failed).error).isInstanceOf(UpdateError.BadRelease::class.java)
        assertThat(store.candidate).isNull()
        assertThat(notifier.events).isEmpty()
    }

    @Test
    fun `the update is downloaded, verified and handed to the installer`() = runTest {
        val updater = createUpdater()
        updater.check(manual = true)
        updater.downloadAndInstall()
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
        updater.downloadAndInstall()
        assertThat(source.downloads).isEqualTo(1)
        assertThat(installer.installed).hasSize(2)
    }

    @Test
    fun `an update started from the notification in a new process takes the release from the store`() = runTest {
        store.candidate = anUpdateCandidate(versionName = NEW, versionCode = 2_026_110_124).copy(sha256 = A_SHA256)
        val updater = createUpdater()
        updater.downloadAndInstall()
        assertThat(installer.installed).hasSize(1)
    }

    @Test
    fun `a download with another checksum is deleted and not installed`() = runTest {
        source.apkContent = "something else".toByteArray()
        val updater = createUpdater()
        updater.check(manual = true)
        updater.downloadAndInstall()
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
        updater.check(manual = true)
        updater.downloadAndInstall()
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.BadApk(ApkRejection.SignatureMismatch))
        assertThat(installer.installed).isEmpty()
        assertThat(temporaryFolder.root.listFiles()).isEmpty()
    }

    @Test
    fun `an APK that Android cannot read, or of another version, is not installed`() = runTest {
        val updater = createUpdater()
        updater.check(manual = true)
        reader.archive = null
        updater.downloadAndInstall()
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.BadApk(ApkRejection.Unreadable))
        reader.archive = ApkInfo(PACKAGE, 2_026_110_129, setOf("release-key"))
        updater.downloadAndInstall()
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.BadApk(ApkRejection.WrongVersion))
        assertThat(installer.installed).isEmpty()
    }

    @Test
    fun `nothing is downloaded without the permission to install, or without the space`() = runTest {
        val updater = createUpdater()
        updater.check(manual = true)
        installer.isAllowed = false
        updater.downloadAndInstall()
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.InstallNotAllowed)
        installer.isAllowed = true
        files.freeBytes = 5
        updater.downloadAndInstall()
        assertThat((updater.state.value as UpdateState.Failed).error).isEqualTo(UpdateError.NoSpace)
        assertThat(source.downloads).isEqualTo(0)
        assertThat(installer.installed).isEmpty()
    }

    @Test
    fun `a download that breaks off fails with the release kept`() = runTest {
        val updater = createUpdater()
        updater.check(manual = true)
        source.failure = IOException("connection reset")
        updater.downloadAndInstall()
        val failed = updater.state.value as UpdateState.Failed
        assertThat(failed.error).isEqualTo(UpdateError.Network("connection reset"))
        assertThat(failed.candidate).isNotNull()
    }

    @Test
    fun `the installer's refusal is kept with its status and message`() = runTest {
        val updater = createUpdater()
        updater.check(manual = true)
        updater.downloadAndInstall()
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
        assertThat(scheduler.events).containsExactly("periodic metered=false", "check soon").inOrder()
    }

    @Test
    fun `the start forgets a release that is installed by now and does not check again within the hour`() = runTest {
        store.candidate = anUpdateCandidate(versionName = "26.11.1-intercom.3", versionCode = 2_026_110_123)
        store.lastCheck = clock.epochMillis() - 60_000
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
        updater.check(manual = true)
        updater.downloadAndInstall()
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
