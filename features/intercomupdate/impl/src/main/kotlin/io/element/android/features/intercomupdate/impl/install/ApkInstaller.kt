/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.intercomupdate.impl.logic.UpdateCandidate
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/** Hands an APK to the system's installer. */
interface ApkInstaller {
    /** Whether the user has allowed InterCom to install apps ("Install unknown apps"). */
    fun isAllowedToInstall(): Boolean

    /**
     * Starts the installation of [apk]. It returns once the system has taken the file; the outcome arrives
     * later at [InterComUpdateReceiver].
     */
    suspend fun install(apk: File, candidate: UpdateCandidate)
}

@ContributesBinding(AppScope::class)
class DefaultApkInstaller(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : ApkInstaller {
    override fun isAllowedToInstall(): Boolean {
        // Before Android 8 the permission is one switch for the whole device, which the installer itself asks for.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    override suspend fun install(apk: File, candidate: UpdateCandidate) = withContext(dispatchers.io) {
        val installer = context.packageManager.packageInstaller
        // A session left over from an attempt that was never confirmed would keep its copy of the APK.
        installer.mySessions.forEach { runCatchingExceptions { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 and newer install an app's update of itself without asking, when the app holds
                // UPDATE_PACKAGES_WITHOUT_USER_ACTION and targets a recent API level. Where the system does not
                // agree it asks as before, see STATUS_PENDING_USER_ACTION in InterComUpdateReceiver.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite(candidate.apkName, 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output, bufferSize = 1 shl 16) }
                    session.fsync(output)
                }
                val intent = Intent(context, InterComUpdateReceiver::class.java)
                    .setAction(InterComUpdateReceiver.ACTION_INSTALL_STATUS)
                    .setPackage(context.packageName)
                // Mutable: the system adds the status to it.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                Timber.tag(TAG).i("Committing install session $sessionId for ${candidate.versionName}")
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            runCatchingExceptions { installer.abandonSession(sessionId) }
            throw e
        }
    }

    private companion object {
        const val TAG = "InterComUpdate"
    }
}
