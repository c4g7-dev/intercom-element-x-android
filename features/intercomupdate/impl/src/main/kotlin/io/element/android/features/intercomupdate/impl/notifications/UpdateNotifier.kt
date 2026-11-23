/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.intercomupdate.impl.R
import io.element.android.features.intercomupdate.impl.install.InterComUpdateReceiver
import io.element.android.features.intercomupdate.impl.logic.UpdateCandidate
import io.element.android.features.intercomupdate.impl.logic.UpdateError
import io.element.android.features.intercomupdate.impl.messageRes
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

/** The updater's one notification, which shows where the update stands. */
interface UpdateNotifier {
    fun showAvailable(candidate: UpdateCandidate)

    /** [progress] in 0..1, or null while the size is unknown or the download is being checked. */
    fun showProgress(candidate: UpdateCandidate, progress: Float?)

    /** Shows the system's question, [confirmation], at once when the app is on screen, behind a notification when not. */
    fun askForConfirmation(confirmation: Intent, candidate: UpdateCandidate?)
    fun showFailed(error: UpdateError, candidate: UpdateCandidate?)
    fun showUpdated(versionName: String)
    fun cancel()
}

@ContributesBinding(AppScope::class)
class DefaultUpdateNotifier(
    @ApplicationContext private val context: Context,
) : UpdateNotifier {
    private val notificationManager = NotificationManagerCompat.from(context)

    override fun showAvailable(candidate: UpdateCandidate) {
        val update = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, InterComUpdateReceiver::class.java).setAction(InterComUpdateReceiver.ACTION_UPDATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify(
            builder(context.getString(R.string.notification_intercom_update_available_title, candidate.versionName))
                .setContentText(context.getString(R.string.notification_intercom_update_available_text))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.notification_intercom_update_action_update), update)
        )
    }

    override fun showProgress(candidate: UpdateCandidate, progress: Float?) {
        notify(
            builder(context.getString(R.string.notification_intercom_update_progress_title, candidate.versionName))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
        )
    }

    override fun askForConfirmation(confirmation: Intent, candidate: UpdateCandidate?) {
        val isOnScreen = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (isOnScreen) {
            try {
                context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                cancel()
                return
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not show the installer's confirmation")
            }
        }
        // Android does not let an app in the background open a screen; the notification does it on a tap.
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify(
            builder(context.getString(R.string.notification_intercom_update_confirm_title, candidate?.versionName.orEmpty()).trim())
                .setContentText(context.getString(R.string.notification_intercom_update_confirm_text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
        )
    }

    override fun showFailed(error: UpdateError, candidate: UpdateCandidate?) {
        val text = context.getString(error.messageRes())
        notify(
            builder(context.getString(R.string.notification_intercom_update_failed_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp())
                .setAutoCancel(true)
        )
    }

    override fun showUpdated(versionName: String) {
        notify(
            builder(context.getString(R.string.notification_intercom_update_updated_title, versionName))
                .setContentText(context.getString(R.string.notification_intercom_update_updated_text))
                .setContentIntent(openApp())
                .setAutoCancel(true)
        )
    }

    override fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun builder(title: String): NotificationCompat.Builder {
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_intercom_update_channel))
                .build()
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
    }

    private fun openApp(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // The permission is the user's to give for the app's notifications as a whole; without it there is none.
    @SuppressLint("MissingPermission")
    private fun notify(builder: NotificationCompat.Builder) {
        if (!notificationManager.areNotificationsEnabled()) return
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val TAG = "InterComUpdate"
        const val CHANNEL_ID = "intercom_updates"
        const val NOTIFICATION_ID = 0x1C0D
    }
}
