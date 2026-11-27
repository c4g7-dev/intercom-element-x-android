/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import android.content.Intent
import io.element.android.features.intercomupdate.impl.data.DeviceInfo
import io.element.android.features.intercomupdate.impl.data.InterComUpdateStore
import io.element.android.features.intercomupdate.impl.data.ReleaseSource
import io.element.android.features.intercomupdate.impl.data.Snooze
import io.element.android.features.intercomupdate.impl.data.UpdateFiles
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.install.ApkInstaller
import io.element.android.features.intercomupdate.impl.logic.ApkInfo
import io.element.android.features.intercomupdate.impl.logic.ApkInfoReader
import io.element.android.features.intercomupdate.impl.logic.FeedAnswer
import io.element.android.features.intercomupdate.impl.logic.FeedMemory
import io.element.android.features.intercomupdate.impl.logic.GitHubRelease
import io.element.android.features.intercomupdate.impl.logic.UpdateCandidate
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import io.element.android.features.intercomupdate.impl.logic.UpdateState
import io.element.android.features.intercomupdate.impl.logic.WhatsNew
import io.element.android.features.intercomupdate.impl.notifications.UpdateNotifier
import io.element.android.features.intercomupdate.impl.work.UpdateScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.IOException

class InMemoryInterComUpdateStore(
    initialSettings: UpdateSettings = UpdateSettings(),
    var candidate: UpdateCandidate? = null,
    var lastCheck: Long = 0,
    var notified: Long = 0,
    var lastCheckAttempt: Long = 0,
    var autoInstallFailed: Long = 0,
) : InterComUpdateStore {
    override val snooze = MutableStateFlow(Snooze())
    override suspend fun setSnooze(snooze: Snooze) {
        this.snooze.value = snooze
    }

    override suspend fun lastCheckAttempt() = lastCheckAttempt
    override suspend fun setLastCheckAttempt(millis: Long) {
        lastCheckAttempt = millis
    }

    override suspend fun autoInstallFailedVersionCode() = autoInstallFailed
    override suspend fun setAutoInstallFailedVersionCode(versionCode: Long) {
        autoInstallFailed = versionCode
    }

    override val settings = MutableStateFlow(initialSettings)
    override suspend fun setSettings(transform: (UpdateSettings) -> UpdateSettings) {
        settings.value = transform(settings.value)
    }

    override suspend fun lastCheck() = lastCheck
    override suspend fun setLastCheck(millis: Long) {
        lastCheck = millis
    }

    override suspend fun candidate() = candidate
    override suspend fun setCandidate(candidate: UpdateCandidate?) {
        this.candidate = candidate
    }

    override suspend fun notifiedVersionCode() = notified
    override suspend fun setNotifiedVersionCode(versionCode: Long) {
        notified = versionCode
    }

    var lastFeedLook = 0L
    override suspend fun lastFeedLook() = lastFeedLook
    override suspend fun setLastFeedLook(millis: Long) {
        lastFeedLook = millis
    }

    var feedMemory = FeedMemory()
    override suspend fun feedMemory() = feedMemory
    override suspend fun setFeedMemory(memory: FeedMemory) {
        feedMemory = memory
    }

    override val whatsNew = MutableStateFlow<WhatsNew?>(null)
    override suspend fun setWhatsNew(whatsNew: WhatsNew?) {
        this.whatsNew.value = whatsNew
    }

    var lastRunVersionCode = 0L
    override suspend fun lastRunVersionCode() = lastRunVersionCode
    override suspend fun setLastRunVersionCode(versionCode: Long) {
        lastRunVersionCode = versionCode
    }

    var whatsNewSince = 0L
    override suspend fun whatsNewSince() = whatsNewSince
    override suspend fun setWhatsNewSince(versionCode: Long) {
        whatsNewSince = versionCode
    }
}

class FakeDeviceInfo(
    override val manufacturer: String = "Google",
    override val sdkInt: Int = 34,
) : DeviceInfo

class FakeReleaseSource(
    var releases: List<GitHubRelease> = emptyList(),
    val texts: MutableMap<String, String> = mutableMapOf(),
    var apkContent: ByteArray = ByteArray(0),
    var failure: IOException? = null,
) : ReleaseSource {
    var downloads = 0
    var releaseRequests = 0
    var feedRequests = 0
    val feedEtags = mutableListOf<String?>()

    /** Null for a feed that does not answer. */
    var feedAnswer: FeedAnswer? = null

    override suspend fun feed(etag: String?): FeedAnswer {
        feedRequests++
        feedEtags += etag
        return feedAnswer ?: throw IOException("no feed")
    }
    var onDownload: () -> Unit = {}

    override suspend fun releases(): List<GitHubRelease> {
        releaseRequests++
        failure?.let { throw it }
        return releases
    }

    override suspend fun text(url: String): String {
        failure?.let { throw it }
        return texts[url] ?: throw IOException("HTTP 404 for $url")
    }

    override suspend fun download(url: String, destination: File, expectedSize: Long, onProgress: (Float?) -> Unit) {
        failure?.let { throw it }
        downloads++
        onDownload()
        onProgress(0.5f)
        destination.parentFile?.mkdirs()
        destination.writeBytes(apkContent)
        onProgress(1f)
    }
}

class FakeApkInfoReader(
    var installed: ApkInfo?,
    var archive: ApkInfo?,
) : ApkInfoReader {
    override fun installed() = installed
    override fun archive(path: String) = archive
}

class FakeApkInstaller(
    var isAllowed: Boolean = true,
) : ApkInstaller {
    val installed = mutableListOf<Pair<File, UpdateCandidate>>()
    override fun isAllowedToInstall() = isAllowed
    override suspend fun install(apk: File, candidate: UpdateCandidate) {
        installed += apk to candidate
    }
}

class FakeUpdateFiles(
    private val directory: File,
    var freeBytes: Long = Long.MAX_VALUE,
) : UpdateFiles {
    override fun apkFile(candidate: UpdateCandidate) = File(directory, candidate.apkName)
    override fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    override fun freeBytes() = freeBytes
}

class FakeUpdateNotifier : UpdateNotifier {
    val events = mutableListOf<String>()
    override fun showAvailable(candidate: UpdateCandidate) {
        events += "available ${candidate.versionName}"
    }

    override fun showProgress(candidate: UpdateCandidate, progress: Float?) = Unit
    override fun askForConfirmation(confirmation: Intent, candidate: UpdateCandidate?) {
        events += "confirm"
    }

    override fun showFailed(error: UpdateError, candidate: UpdateCandidate?) {
        events += "failed"
    }

    override fun showUpdated(versionName: String) {
        events += "updated $versionName"
    }

    override fun cancel() = Unit
}

class FakeUpdateScheduler : UpdateScheduler {
    val events = mutableListOf<String>()
    override fun schedulePeriodicCheck(settings: UpdateSettings) {
        events += if (settings.checkAutomatically) "periodic metered=${settings.allowMetered}" else "no periodic"
    }

    override fun enqueueUpdate(settings: UpdateSettings, userInitiated: Boolean) {
        events += if (userInitiated) "update asked" else "update auto"
    }
}

class FakeInterComUpdater(
    initialState: UpdateState = UpdateState.Idle,
    override val isSupported: Boolean = true,
) : InterComUpdater {
    override val state = MutableStateFlow(initialState)
    val calls = mutableListOf<String>()

    override suspend fun start() = Unit
    override suspend fun onAppOpened() = Unit
    override suspend fun onSettingsChanged() = Unit
    override suspend fun check(trigger: CheckTrigger) = Unit
    override fun requestUpdate() {
        calls += "requestUpdate"
    }

    override suspend fun snooze() {
        calls += "snooze"
    }

    override suspend fun downloadAndInstall(userInitiated: Boolean) = UpdateRun.Done
    override fun onInstallStatus(status: Int, message: String?, confirmation: Intent?) = Unit
    override fun onConfirmationAbandoned() {
        calls += "onConfirmationAbandoned"
        state.value = UpdateState.Failed(UpdateError.NotConfirmed, state.value.candidate)
    }

    override suspend fun dismissWhatsNew() {
        calls += "dismissWhatsNew"
    }

    override fun onAppReplaced() = Unit
}
