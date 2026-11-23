/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl

import androidx.annotation.StringRes
import io.element.android.features.intercomupdate.impl.install.InstallFailure
import io.element.android.features.intercomupdate.impl.logic.ApkRejection
import io.element.android.features.intercomupdate.impl.logic.UpdateError

/** What went wrong, in plain words. */
@StringRes
fun UpdateError.messageRes(): Int = when (this) {
    is UpdateError.Network -> R.string.screen_intercom_update_error_network
    is UpdateError.BadRelease -> R.string.screen_intercom_update_error_bad_release
    UpdateError.NoSpace -> R.string.screen_intercom_update_error_no_space
    UpdateError.ChecksumMismatch -> R.string.screen_intercom_update_error_checksum
    is UpdateError.BadApk -> when (reason) {
        ApkRejection.Unreadable -> R.string.screen_intercom_update_error_apk_unreadable
        ApkRejection.WrongPackage,
        ApkRejection.WrongVersion,
        ApkRejection.NotNewer -> R.string.screen_intercom_update_error_apk_wrong
        ApkRejection.NoSignature -> R.string.screen_intercom_update_error_apk_no_signature
        ApkRejection.SignatureMismatch -> R.string.screen_intercom_update_error_apk_signature
    }
    UpdateError.InstallNotAllowed -> R.string.screen_intercom_update_error_install_not_allowed
    is UpdateError.Install -> when (InstallFailure.of(status)) {
        InstallFailure.Aborted -> R.string.screen_intercom_update_error_install_aborted
        InstallFailure.Blocked -> R.string.screen_intercom_update_error_install_blocked
        InstallFailure.Conflict -> R.string.screen_intercom_update_error_install_conflict
        InstallFailure.Incompatible -> R.string.screen_intercom_update_error_install_incompatible
        InstallFailure.Invalid -> R.string.screen_intercom_update_error_install_invalid
        InstallFailure.Storage -> R.string.screen_intercom_update_error_install_storage
        InstallFailure.Timeout -> R.string.screen_intercom_update_error_install_timeout
        InstallFailure.Other -> R.string.screen_intercom_update_error_install_other
    }
}

/** What the system or the network said in its own words, shown small below the message. */
fun UpdateError.detail(): String? = when (this) {
    is UpdateError.Network -> detail
    is UpdateError.BadRelease -> detail
    is UpdateError.Install -> detail
    else -> null
}
