/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

/** Why a check or an update did not succeed. */
sealed interface UpdateError {
    /** The release list or a file could not be fetched. */
    data class Network(val detail: String?) : UpdateError

    /** What the release says about itself does not fit together. */
    data class BadRelease(val detail: String?) : UpdateError

    /** Not enough free storage for the download and the installation. */
    data object NoSpace : UpdateError

    /** The downloaded file is not the one the release names. */
    data object ChecksumMismatch : UpdateError

    data class BadApk(val reason: ApkRejection) : UpdateError

    /** InterCom is not allowed to install apps yet. */
    data object InstallNotAllowed : UpdateError

    /** The system's installer refused or failed; [status] is `PackageInstaller.STATUS_*`. */
    data class Install(val status: Int, val detail: String?) : UpdateError
}

sealed interface UpdateState {
    /** The candidate this state is about, if any. */
    val candidate: UpdateCandidate? get() = null

    /** Nothing known: no check has been made since the app started. */
    data object Idle : UpdateState

    data class Checking(override val candidate: UpdateCandidate? = null) : UpdateState

    data object UpToDate : UpdateState

    data class Available(override val candidate: UpdateCandidate) : UpdateState

    /** [progress] in 0..1, or null when the size is not known. */
    data class Downloading(override val candidate: UpdateCandidate, val progress: Float?) : UpdateState

    data class Verifying(override val candidate: UpdateCandidate) : UpdateState

    /** Handed to the system's installer. */
    data class Installing(override val candidate: UpdateCandidate) : UpdateState

    /** The system asks the user to confirm. */
    data class AwaitingConfirmation(override val candidate: UpdateCandidate) : UpdateState

    /** [candidate] is kept when there is one, so that the update can be tried again. */
    data class Failed(val error: UpdateError, override val candidate: UpdateCandidate? = null) : UpdateState

    /** An update is on its way: a check must not replace it, and a second one must not start. */
    val isBusy: Boolean
        get() = this is Downloading || this is Verifying || this is Installing || this is AwaitingConfirmation
}

sealed interface UpdateEvent {
    data object CheckStarted : UpdateEvent
    data object NothingNewer : UpdateEvent
    data class Found(val candidate: UpdateCandidate) : UpdateEvent
    data class CheckFailed(val error: UpdateError) : UpdateEvent
    data object DownloadStarted : UpdateEvent
    data class DownloadProgress(val progress: Float?) : UpdateEvent
    data object Downloaded : UpdateEvent
    data object Verified : UpdateEvent
    data object ConfirmationRequested : UpdateEvent
    data class UpdateFailed(val error: UpdateError) : UpdateEvent
}

/**
 * The updater's state machine: the state after [event], or the same state when the event does not apply to it
 * (a progress report after a failure, the result of a check that an update overtook).
 */
fun UpdateState.reduce(event: UpdateEvent): UpdateState {
    val current = candidate
    return when (event) {
        UpdateEvent.CheckStarted -> if (isBusy) this else UpdateState.Checking(current)
        UpdateEvent.NothingNewer -> if (this is UpdateState.Checking) UpdateState.UpToDate else this
        is UpdateEvent.Found -> if (this is UpdateState.Checking) UpdateState.Available(event.candidate) else this
        is UpdateEvent.CheckFailed -> when {
            this !is UpdateState.Checking -> this
            // A release found earlier stays on offer when a later check fails, say without a network.
            current != null -> UpdateState.Available(current)
            else -> UpdateState.Failed(event.error)
        }
        UpdateEvent.DownloadStarted -> when {
            current == null || isBusy || this is UpdateState.Checking -> this
            else -> UpdateState.Downloading(current, 0f)
        }
        is UpdateEvent.DownloadProgress -> if (this is UpdateState.Downloading) copy(progress = event.progress) else this
        UpdateEvent.Downloaded -> if (this is UpdateState.Downloading) UpdateState.Verifying(candidate) else this
        UpdateEvent.Verified -> if (this is UpdateState.Verifying) UpdateState.Installing(candidate) else this
        UpdateEvent.ConfirmationRequested -> if (this is UpdateState.Installing) UpdateState.AwaitingConfirmation(candidate) else this
        // Also when nothing is under way: the installer may answer after the app was restarted.
        is UpdateEvent.UpdateFailed -> if (this is UpdateState.Checking) this else UpdateState.Failed(event.error, current)
    }
}
