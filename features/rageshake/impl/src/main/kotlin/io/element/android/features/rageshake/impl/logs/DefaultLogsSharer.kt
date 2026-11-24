/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.rageshake.impl.logs

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.rageshake.api.logs.LogRedaction
import io.element.android.features.rageshake.api.logs.LogsSharer
import io.element.android.features.rageshake.api.reporter.BugReporter
import io.element.android.libraries.androidutils.file.safeDelete
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.di.CacheDirectory
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Where the zip is written, under the cache directory, which the app's FileProvider serves. */
internal const val SHARED_LOGS_DIRECTORY_NAME = "shared_logs"

/**
 * InterCom: zips the log directory (the SDK's and the app's log files, and a fresh dump of the app's own logcat) with
 * every line redacted, and opens the share sheet on it. The previous zip is deleted first, so at most one is kept.
 */
@ContributesBinding(AppScope::class)
class DefaultLogsSharer(
    @ApplicationContext private val context: Context,
    @CacheDirectory private val cacheDirectory: File,
    private val bugReporter: BugReporter,
    private val buildMeta: BuildMeta,
    private val dispatchers: CoroutineDispatchers,
) : LogsSharer {
    override suspend fun share(): Result<Unit> = runCatchingExceptions {
        val zip = withContext(dispatchers.io) {
            // Written into the log directory, so that it is zipped with the rest; removed again once it is.
            val logcat = bugReporter.saveLogCat()
            try {
                val files = bugReporter.logDirectory().listFiles().orEmpty().filter { it.isFile }
                check(files.isNotEmpty()) { "There are no log files" }
                val directory = File(cacheDirectory, SHARED_LOGS_DIRECTORY_NAME)
                directory.listFiles()?.forEach { it.safeDelete() }
                directory.mkdirs()
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val zip = File(directory, "${buildMeta.applicationName.lowercase(Locale.US)}-logs-$stamp.zip")
                zip.outputStream().use { zipRedactedLogs(files, it) }
                zip
            } finally {
                logcat?.safeDelete()
            }
        }
        Timber.i("Sharing the logs, ${zip.length()} bytes")
        val uri = FileProvider.getUriForFile(context, "${buildMeta.applicationId}.fileprovider", zip)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "${buildMeta.applicationName} ${buildMeta.versionName} logs")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(dispatchers.main) {
            context.startActivity(
                Intent.createChooser(send, null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }.onFailure {
        Timber.w(it, "Could not share the logs")
    }
}

/**
 * Writes [files] to [output] as a zip, oldest first, each text file redacted line by line. A gzip file (a rotated log
 * the SDK compressed) is copied as it is: there is nothing to redact in it without unpacking it.
 */
internal fun zipRedactedLogs(files: List<File>, output: OutputStream) {
    ZipOutputStream(output.buffered()).use { zip ->
        for (file in files.sortedBy { it.lastModified() }) {
            zip.putNextEntry(ZipEntry(file.name).apply { time = file.lastModified() })
            file.inputStream().buffered().use { input ->
                if (input.isGzip()) {
                    input.copyTo(zip)
                } else {
                    val writer = zip.bufferedWriter()
                    input.bufferedReader().forEachLine { line ->
                        writer.write(LogRedaction.redact(line))
                        writer.write("\n")
                    }
                    // Flushed, not closed: closing would close the zip.
                    writer.flush()
                }
            }
            zip.closeEntry()
        }
    }
}

private fun InputStream.isGzip(): Boolean {
    mark(2)
    val first = read()
    val second = read()
    reset()
    return first == 0x1f && second == 0x8b
}
