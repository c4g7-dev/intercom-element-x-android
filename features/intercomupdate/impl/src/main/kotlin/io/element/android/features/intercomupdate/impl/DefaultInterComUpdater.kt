/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import android.content.Intent
import android.content.pm.PackageInstaller
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.intercomupdate.impl.data.InterComUpdateStore
import io.element.android.features.intercomupdate.impl.data.ReleaseSource
import io.element.android.features.intercomupdate.impl.data.UpdateFiles
import io.element.android.features.intercomupdate.impl.install.ApkInstaller
import io.element.android.features.intercomupdate.impl.install.InstallFailure
import io.element.android.features.intercomupdate.impl.logic.ApkInfoReader
import io.element.android.features.intercomupdate.impl.logic.ApkVerifier
import io.element.android.features.intercomupdate.impl.logic.ReleaseSelector
import io.element.android.features.intercomupdate.impl.logic.Sha256
import io.element.android.features.intercomupdate.impl.logic.UpdateCandidate
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import io.element.android.features.intercomupdate.impl.logic.UpdateEvent
import io.element.android.features.intercomupdate.impl.logic.UpdateManifest
import io.element.android.features.intercomupdate.impl.logic.UpdateState
import io.element.android.features.intercomupdate.impl.logic.reduce
import io.element.android.features.intercomupdate.impl.notifications.UpdateNotifier
import io.element.android.features.intercomupdate.impl.work.UpdateScheduler
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import kotlin.time.Duration.Companion.hours

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultInterComUpdater(
    private val buildMeta: BuildMeta,
    private val store: InterComUpdateStore,
    private val releaseSource: ReleaseSource,
    private val apkInfoReader: ApkInfoReader,
    private val installer: ApkInstaller,
    private val files: UpdateFiles,
    private val notifier: UpdateNotifier,
    private val scheduler: UpdateScheduler,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : InterComUpdater {
    private val json = Json { ignoreUnknownKeys = true }
    private val checkMutex = Mutex()
    private val updateMutex = Mutex()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    override val isSupported: Boolean = buildMeta.buildType == BuildType.RELEASE

    private fun dispatch(event: UpdateEvent): UpdateState = _state.updateAndGet { it.reduce(event) }

    /** The version code Android has for the running app, which is what it compares an update against. */
    private fun currentVersionCode(): Long = apkInfoReader.installed()?.versionCode ?: buildMeta.versionCode

    override suspend fun start() {
        if (!isSupported) return
        val settings = store.settings.first()
        val known = store.candidate()
        if (known != null) {
            if (known.versionCode > currentVersionCode()) {
                _state.update { if (it is UpdateState.Idle) UpdateState.Available(known) else it }
            } else {
                forget()
            }
        }
        scheduler.schedulePeriodicCheck(settings)
        if (settings.checkAutomatically && clock.epochMillis() - store.lastCheck() > MIN_START_CHECK_INTERVAL.inWholeMilliseconds) {
            scheduler.checkSoon(settings)
        }
    }

    override suspend fun onSettingsChanged() {
        if (!isSupported) return
        scheduler.schedulePeriodicCheck(store.settings.first())
    }

    override suspend fun check(manual: Boolean) {
        if (!isSupported) return
        checkMutex.withLock {
            if (dispatch(UpdateEvent.CheckStarted) !is UpdateState.Checking) return
            try {
                val settings = store.settings.first()
                val currentVersionCode = currentVersionCode()
                val selected = ReleaseSelector.select(releaseSource.releases(), currentVersionCode, settings.includePrereleases)
                if (selected == null) {
                    store.setLastCheck(clock.epochMillis())
                    forget()
                    dispatch(UpdateEvent.NothingNewer)
                    return
                }
                val manifest = selected.manifest?.let { json.decodeFromString<UpdateManifest>(releaseSource.text(it.downloadUrl)) }
                val sums = if (manifest == null) selected.sha256Sums?.let { releaseSource.text(it.downloadUrl) } else null
                val candidate = ReleaseSelector.toCandidate(selected, manifest, sums, buildMeta.applicationId, currentVersionCode)
                store.setLastCheck(clock.epochMillis())
                store.setCandidate(candidate)
                Timber.tag(TAG).i("Found ${candidate.versionName} (${candidate.versionCode})")
                dispatch(UpdateEvent.Found(candidate))
                if (!manual && store.notifiedVersionCode() < candidate.versionCode) {
                    notifier.showAvailable(candidate)
                    store.setNotifiedVersionCode(candidate.versionCode)
                }
            } catch (e: CancellationException) {
                dispatch(UpdateEvent.CheckFailed(UpdateError.Network(null)))
                throw e
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Update check failed")
                dispatch(UpdateEvent.CheckFailed(UpdateError.Network(e.message)))
            } catch (e: SerializationException) {
                Timber.tag(TAG).w(e, "Update check: unreadable release")
                dispatch(UpdateEvent.CheckFailed(UpdateError.BadRelease(e.message)))
            } catch (e: IllegalArgumentException) {
                Timber.tag(TAG).w(e, "Update check: inconsistent release")
                dispatch(UpdateEvent.CheckFailed(UpdateError.BadRelease(e.message)))
            }
        }
    }

    override fun requestUpdate() {
        if (!isSupported) return
        coroutineScope.launch { scheduler.enqueueUpdate(store.settings.first()) }
    }

    override suspend fun downloadAndInstall() {
        if (!isSupported || !updateMutex.tryLock()) return
        try {
            if (_state.value.candidate == null) {
                // Started from the notification in a new process: the candidate is in the store only.
                val known = store.candidate() ?: return
                _state.update { if (it is UpdateState.Idle || it is UpdateState.UpToDate) UpdateState.Available(known) else it }
            }
            val started = dispatch(UpdateEvent.DownloadStarted) as? UpdateState.Downloading ?: return
            val error = run(started.candidate)
            if (error != null) fail(error)
        } finally {
            updateMutex.unlock()
        }
    }

    /** Returns what went wrong, or null once the system's installer has the APK. */
    private suspend fun run(candidate: UpdateCandidate): UpdateError? {
        try {
            if (!installer.isAllowedToInstall()) return UpdateError.InstallNotAllowed
            if (candidate.versionCode <= currentVersionCode()) {
                forget()
                _state.value = UpdateState.UpToDate
                return null
            }
            val apk = files.apkFile(candidate)
            val alreadyThere = withContext(dispatchers.io) { apk.isFile && Sha256.matches(apk, candidate.sha256) }
            if (!alreadyThere) {
                withContext(dispatchers.io) { files.clear() }
                // The download, the copy the installer makes of it, and the installed app.
                if (files.freeBytes() < candidate.apkSize * FREE_SPACE_FACTOR) return UpdateError.NoSpace
                notifier.showProgress(candidate, 0f)
                releaseSource.download(candidate.apkUrl, apk, candidate.apkSize) { progress ->
                    dispatch(UpdateEvent.DownloadProgress(progress))
                    notifier.showProgress(candidate, progress)
                }
            }
            dispatch(UpdateEvent.Downloaded)
            notifier.showProgress(candidate, null)
            val problem = withContext(dispatchers.io) {
                if (!Sha256.matches(apk, candidate.sha256)) {
                    UpdateError.ChecksumMismatch
                } else {
                    ApkVerifier.check(apkInfoReader.archive(apk.path), apkInfoReader.installed(), candidate.versionCode)
                        ?.let { UpdateError.BadApk(it) }
                }
            }
            if (problem != null) {
                withContext(dispatchers.io) { files.clear() }
                return problem
            }
            dispatch(UpdateEvent.Verified)
            installer.install(apk, candidate)
            return null
        } catch (e: CancellationException) {
            fail(UpdateError.Network(null), notify = false)
            throw e
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Update failed")
            return UpdateError.Network(e.message)
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "Update refused")
            return UpdateError.Install(PackageInstaller.STATUS_FAILURE_BLOCKED, e.message)
        }
    }

    private fun fail(error: UpdateError, notify: Boolean = true) {
        Timber.tag(TAG).w("Update failed: $error")
        val state = dispatch(UpdateEvent.UpdateFailed(error))
        if (notify) notifier.showFailed(error, state.candidate) else notifier.cancel()
    }

    override fun onInstallStatus(status: Int, message: String?, confirmation: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val state = dispatch(UpdateEvent.ConfirmationRequested)
                if (confirmation != null) notifier.askForConfirmation(confirmation, state.candidate)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Seldom seen: Android stops the app to replace it, and the new version starts in onAppReplaced.
                _state.value = UpdateState.UpToDate
                coroutineScope.launch { forget() }
            }
            else -> {
                val error = UpdateError.Install(status, message)
                // Saying no to the system's question needs no notification saying so.
                fail(error, notify = InstallFailure.of(status) != InstallFailure.Aborted)
                coroutineScope.launch { if (_state.value.candidate == null) restoreCandidate(error) }
            }
        }
    }

    /** After a restart between handing over the APK and the installer's answer, the failure has lost its release. */
    private suspend fun restoreCandidate(error: UpdateError) {
        val known = store.candidate() ?: return
        _state.update { if (it is UpdateState.Failed && it.candidate == null) UpdateState.Failed(error, known) else it }
    }

    override fun onAppReplaced() {
        coroutineScope.launch {
            forget()
            if (!isSupported) return@launch
            notifier.showUpdated(buildMeta.versionName)
            scheduler.schedulePeriodicCheck(store.settings.first())
        }
    }

    private suspend fun forget() {
        store.setCandidate(null)
        withContext(dispatchers.io) { files.clear() }
    }

    private companion object {
        const val TAG = "InterComUpdate"
        const val FREE_SPACE_FACTOR = 3
        val MIN_START_CHECK_INTERVAL = 1.hours
    }
}
