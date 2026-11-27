/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.intercomupdate.impl.logic.FeedAnswer
import io.element.android.features.intercomupdate.impl.logic.GitHubRelease
import io.element.android.features.intercomupdate.impl.logic.ReleaseFeed
import io.element.android.features.intercomupdate.impl.logic.ReleaseSelector
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Where the releases come from: GitHub. */
interface ReleaseSource {
    /** The newest releases of the InterCom repository, the desktop's among them. */
    suspend fun releases(): List<GitHubRelease>

    /**
     * The releases feed, asked for with the [etag] of the last answer. It is outside of the API's request limit.
     * [FeedAnswer.Failed] is never returned: a failure is an [IOException].
     */
    suspend fun feed(etag: String?): FeedAnswer

    /** The text of a small file of a release. */
    suspend fun text(url: String): String

    /** Downloads [url] to [destination], reporting the progress in 0..1 (null when the size is unknown). */
    suspend fun download(url: String, destination: File, expectedSize: Long, onProgress: (Float?) -> Unit)
}

@ContributesBinding(AppScope::class)
class DefaultReleaseSource(
    okHttpClient: OkHttpClient,
    private val dispatchers: CoroutineDispatchers,
) : ReleaseSource {
    // Redirects are followed (a release's files are served from another GitHub host), but never down to http.
    private val client = okHttpClient.newBuilder().followRedirects(true).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun releases(): List<GitHubRelease> {
        val body = get(RELEASES_URL, accept = "application/vnd.github+json")
        return json.decodeFromString<List<GitHubRelease>>(body)
    }

    override suspend fun feed(etag: String?): FeedAnswer = withContext(dispatchers.io) {
        val request = Request.Builder().url(ReleaseFeed.URL)
            .header("Accept", "application/atom+xml")
            .apply { if (!etag.isNullOrBlank()) header("If-None-Match", etag) }
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == HTTP_NOT_MODIFIED -> FeedAnswer.NotModified
                !response.isSuccessful -> throw IOException("HTTP ${response.code} for ${ReleaseFeed.URL}")
                else -> {
                    val source = response.body.source()
                    if (source.request(MAX_TEXT_BYTES + 1)) throw IOException("${ReleaseFeed.URL} is too large")
                    try {
                        ReleaseFeed.parse(source.readUtf8(), response.header("ETag"))
                    } catch (e: IllegalArgumentException) {
                        throw IOException("${ReleaseFeed.URL}: ${e.message}", e)
                    }
                }
            }
        }
    }

    override suspend fun text(url: String): String {
        requireReleaseUrl(url)
        return get(url, accept = null)
    }

    private suspend fun get(url: String, accept: String?): String = withContext(dispatchers.io) {
        val request = Request.Builder().url(url).apply { if (accept != null) header("Accept", accept) }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val source = response.body.source()
            // The release list is some hundred KB at most; nothing that is read as text is larger.
            if (!source.request(MAX_TEXT_BYTES + 1)) source.readUtf8() else throw IOException("$url is too large")
        }
    }

    override suspend fun download(url: String, destination: File, expectedSize: Long, onProgress: (Float?) -> Unit) = withContext(dispatchers.io) {
        requireReleaseUrl(url)
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val total = response.body.contentLength().takeIf { it > 0 } ?: expectedSize.takeIf { it > 0 }
            if (total != null && total > MAX_APK_BYTES) throw IOException("$url is too large: $total bytes")
            destination.parentFile?.mkdirs()
            destination.outputStream().use { output ->
                val input = response.body.byteStream()
                val buffer = ByteArray(1 shl 16)
                var done = 0L
                var lastReported = -1
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (done > MAX_APK_BYTES) throw IOException("$url is too large")
                    if (total != null) {
                        val percent = (done * 100 / total).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    } else if (lastReported < 0) {
                        lastReported = 0
                        onProgress(null)
                    }
                }
                output.fd.sync()
            }
        }
    }

    private fun requireReleaseUrl(url: String) {
        if (!url.startsWith(ReleaseSelector.DOWNLOAD_PREFIX)) throw IOException("Not a file of an InterCom release: $url")
    }

    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/c4g7-dev/InterCom/releases?per_page=30"
        private const val HTTP_NOT_MODIFIED = 304
        private const val MAX_TEXT_BYTES = 2L * 1024 * 1024
        private const val MAX_APK_BYTES = 600L * 1024 * 1024
    }
}
