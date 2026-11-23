/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import android.content.Intent
import io.element.android.features.intercomupdate.impl.data.InterComUpdateStore
import io.element.android.features.intercomupdate.impl.data.ReleaseSource
import io.element.android.features.intercomupdate.impl.data.UpdateFiles
import io.element.android.features.intercomupdate.impl.data.UpdateSettings
import io.element.android.features.intercomupdate.impl.install.ApkInstaller
import io.element.android.features.intercomupdate.impl.logic.ApkInfo
import io.element.android.features.intercomupdate.impl.logic.ApkInfoReader
import io.element.android.features.intercomupdate.impl.logic.GitHubRelease
import io.element.android.features.intercomupdate.impl.logic.UpdateCandidate
import io.element.android.features.intercomupdate.impl.logic.UpdateError
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
) : InterComUpdateStore {
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
}

class FakeReleaseSource(
    var releases: List<GitHubRelease> = emptyList(),
    val texts: MutableMap<String, String> = mutableMapOf(),
    var apkContent: ByteArray = ByteArray(0),
    var failure: IOException? = null,
) : ReleaseSource {
    var downloads = 0

    override suspend fun releases(): List<GitHubRelease> {
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

    override fun checkSoon(settings: UpdateSettings) {
        events += "check soon"
    }

    override fun enqueueUpdate(settings: UpdateSettings) {
        events += "update"
    }
}
