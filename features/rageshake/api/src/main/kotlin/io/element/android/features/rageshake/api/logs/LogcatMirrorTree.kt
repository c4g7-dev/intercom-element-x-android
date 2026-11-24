/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.rageshake.api.logs

import android.annotation.SuppressLint
import android.util.Log
import timber.log.Timber

/**
 * InterCom: mirrors part of the app's log to logcat in a build that otherwise writes it only to its own files, so that
 * a call that fails can be followed with `adb logcat` on the phone it fails on.
 *
 * Every warning and error, whatever logged it, under [TAG]; and from debug up everything about calls under
 * [CALL_TAG]: the native call library (`ElementCall:`), the app's side of it (`NativeCall:`), its Matrix backend
 * (`MatrixRTC`), the call's audio, and the RTC core's own records, which keep their tag `matrix-rtc`. Each line goes
 * through [LogRedaction] first.
 *
 * Not planted when "Print logs to logcat" is on: the SDK's tracing then prints everything already.
 *
 * Reading it: `adb logcat -s InterComCall matrix-rtc InterCom`.
 */
class LogcatMirrorTree(
    private val printer: (priority: Int, tag: String, message: String) -> Unit = ::printToLogcat,
) : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.DEBUG

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val outTag = when {
            tag != null && tag in CALL_LOG_TAGS -> tag
            isAboutCalls(tag, message) -> CALL_TAG
            priority >= Log.WARN -> TAG
            else -> return
        }
        val text = LogRedaction.redact(if (tag != null && outTag != tag) "[$tag] $message" else message)
        // Timber has already appended the throwable's stack trace to the message.
        var start = 0
        while (start < text.length) {
            val end = minOf(text.length, start + MAX_LOG_LENGTH)
            printer(priority, outTag, text.substring(start, end))
            start = end
        }
        if (text.isEmpty()) printer(priority, outTag, text)
    }

    companion object {
        const val TAG = "InterCom"
        const val CALL_TAG = "InterComCall"

        /** The RTC core's records, as element-call-android hands them to Timber. */
        const val CORE_TAG = "matrix-rtc"

        private val CALL_LOG_TAGS = setOf(CORE_TAG, CALL_TAG)

        /** What the call code's lines start with: none of them is tagged. */
        private val CALL_PREFIXES = listOf(
            "ElementCall",
            "NativeCall",
            "Native call",
            "MatrixRTC",
            "MatrixRtc",
            "CallAudio",
            "AudioFocus",
            "Audio:",
            "Call:",
            "ElementCallActivity",
            "CallScreen",
        )

        // Below logcat's own limit of about 4 KB a line.
        private const val MAX_LOG_LENGTH = 4000

        internal fun isAboutCalls(tag: String?, message: String): Boolean =
            CALL_PREFIXES.any { message.startsWith(it) } ||
                tag != null && CALL_PREFIXES.any { tag.startsWith(it) }
    }
}

// The one place logs are meant to reach logcat: this tree is the sink, as Timber's DebugTree is in a debug build.
@SuppressLint("LogNotTimber")
private fun printToLogcat(priority: Int, tag: String, message: String) {
    Log.println(priority, tag, message)
}
