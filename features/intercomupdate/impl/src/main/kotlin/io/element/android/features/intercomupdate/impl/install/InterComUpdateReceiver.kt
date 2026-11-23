/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import io.element.android.features.intercomupdate.impl.InterComUpdateBindings
import io.element.android.libraries.architecture.bindings
import timber.log.Timber

/**
 * Receives what happens outside of the app's own code during an update: the installer's verdict, the
 * "Update" button of the notification, and the first start of the new version.
 */
class InterComUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = context.bindings<InterComUpdateBindings>().interComUpdater()
        when (intent.action) {
            ACTION_INSTALL_STATUS -> {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Timber.tag(TAG).i("Install status $status: $message")
                val confirmation = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) intent.confirmationIntent() else null
                updater.onInstallStatus(status, message, confirmation)
            }
            ACTION_UPDATE -> updater.requestUpdate()
            Intent.ACTION_MY_PACKAGE_REPLACED -> updater.onAppReplaced()
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.confirmationIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "io.element.android.features.intercomupdate.INSTALL_STATUS"
        const val ACTION_UPDATE = "io.element.android.features.intercomupdate.UPDATE"
        private const val TAG = "InterComUpdate"
    }
}
