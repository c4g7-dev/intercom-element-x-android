/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.FileInfo
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.media.ThumbnailInfo
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.libraries.matrix.test.media.FakeMediaUploadHandler
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import io.element.android.libraries.mediaupload.test.FakeMediaPreProcessor
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

class DefaultMediaSenderTest : RobolectricTest() {
    private val mediaOptimizationConfig = MediaOptimizationConfig(
        compressImages = true,
        videoCompressionPreset = VideoCompressionPreset.STANDARD,
    )

    @Test
    fun `given an attachment when sending it the preprocessor always runs`() = runTest {
        val preProcessor = FakeMediaPreProcessor()
        val sender = createDefaultMediaSender(
            preProcessor = preProcessor,
            room = FakeJoinedRoom(
                liveTimeline = FakeTimeline().apply {
                    sendFileLambda = lambdaRecorder<
                        File,
                        FileInfo,
                        String?,
                        String?,
                        EventId?,
                        Result<FakeMediaUploadHandler>,
                        > { _, _, _, _, _ ->
                        Result.success(FakeMediaUploadHandler())
                    }
                },
            )
        )

        val uri = Uri.parse("content://image.jpg")
        sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)

        assertThat(preProcessor.processCallCount).isEqualTo(1)
    }

    @Test
    fun `given an attachment when sending it the Room will call sendMedia`() = runTest {
        val sendImageResult =
            lambdaRecorder { _: File, _: File?, _: ImageInfo, _: String?, _: String?, _: EventId? ->
                Result.success(FakeMediaUploadHandler())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendImageLambda = sendImageResult
            },
        )
        val sender = createDefaultMediaSender(room = room)

        val uri = Uri.parse("content://image.jpg")
        sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)
    }

    @Test
    fun `given a failure in the preprocessor when sending the whole process fails`() = runTest {
        val preProcessor = FakeMediaPreProcessor().apply {
            givenResult(Result.failure(Exception()))
        }
        val sender = createDefaultMediaSender(preProcessor)

        val uri = Uri.parse("content://image.jpg")
        val result = sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)

        assertThat(result.exceptionOrNull()).isNotNull()
    }

    @Test
    fun `given a failure in the media upload when sending the whole process fails`() = runTest {
        val preProcessor = FakeMediaPreProcessor().apply {
            givenImageResult()
        }
        val sendImageResult =
            lambdaRecorder { _: File, _: File?, _: ImageInfo, _: String?, _: String?, _: EventId? ->
                Result.failure<FakeMediaUploadHandler>(Exception())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendImageLambda = sendImageResult
            },
        )
        val sender = createDefaultMediaSender(
            preProcessor = preProcessor,
            room = room,
        )

        val uri = Uri.parse("content://image.jpg")
        val result = sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)

        assertThat(result.exceptionOrNull()).isNotNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `given a cancellation in the media upload when sending the job is cancelled`() = runTest(StandardTestDispatcher()) {
        val sendFileResult =
            lambdaRecorder<File, FileInfo, String?, String?, EventId?, Result<FakeMediaUploadHandler>> { _, _, _, _, _ ->
                Result.success(FakeMediaUploadHandler())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendFileLambda = sendFileResult
            },
        )
        val sender = createDefaultMediaSender(room = room)
        val sendJob = launch {
            val uri = Uri.parse("content://image.jpg")
            sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)
        }
        // Wait until several internal tasks run and the file is being uploaded
        advanceTimeBy(3L)

        // Assert the file is being uploaded
        assertThat(sender.hasOngoingMediaUploads).isTrue()

        // Cancel the coroutine
        sendJob.cancel()

        // Wait for the coroutine cleanup to happen
        advanceTimeBy(1L)

        // Assert the file is not being uploaded anymore
        assertThat(sender.hasOngoingMediaUploads).isFalse()
        sendFileResult.assertions().isCalledOnce()
    }

    @Test
    fun `given a thread, the created timeline is closed once the media is sent`() = runTest {
        val threadedTimeline = FakeTimeline(mode = Timeline.Mode.Thread(A_THREAD_ID)).apply {
            sendFileLambda = { _, _, _, _, _ -> Result.success(FakeMediaUploadHandler()) }
        }
        val liveTimeline = FakeTimeline()
        val room = FakeJoinedRoom(
            liveTimeline = liveTimeline,
            createTimelineResult = { Result.success(threadedTimeline) },
        )
        val sender = createDefaultMediaSender(
            room = room,
            timelineMode = Timeline.Mode.Thread(A_THREAD_ID),
        )

        val uri = Uri.parse("content://image.jpg")
        val result = sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)

        assertThat(result.isSuccess).isTrue()
        assertThat(threadedTimeline.closeCounter).isEqualTo(1)
        assertThat(liveTimeline.closeCounter).isEqualTo(0)
    }

    @Test
    fun `given a live timeline, the timeline is not closed once the media is sent`() = runTest {
        val liveTimeline = FakeTimeline().apply {
            sendFileLambda = { _, _, _, _, _ -> Result.success(FakeMediaUploadHandler()) }
        }
        val sender = createDefaultMediaSender(room = FakeJoinedRoom(liveTimeline = liveTimeline))

        val uri = Uri.parse("content://image.jpg")
        val result = sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)

        assertThat(result.isSuccess).isTrue()
        assertThat(liveTimeline.closeCounter).isEqualTo(0)
    }

    private fun anImage(name: String, withThumbnail: Boolean = true): MediaUploadInfo.Image {
        val directory = File(System.getProperty("java.io.tmpdir"), "intercom-batch-test").apply { mkdirs() }
        val file = File(directory, name).apply { writeBytes(name.toByteArray()) }
        val thumbnail = File(directory, "thumbnail-$name").apply { writeBytes(byteArrayOf(1, 2, 3)) }.takeIf { withThumbnail }
        return MediaUploadInfo.Image(
            file = file,
            imageInfo = ImageInfo(
                height = 600,
                width = 800,
                mimetype = MimeTypes.Jpeg,
                size = file.length(),
                thumbnailInfo = ThumbnailInfo(height = 60, width = 80, mimetype = MimeTypes.Jpeg, size = 3),
                thumbnailSource = null,
                blurhash = null,
            ),
            thumbnailFile = thumbnail,
        )
    }

    private class Uploads {
        val calls = mutableListOf<Pair<String, ByteArray>>()
        var failAt = -1
        val upload: suspend (String, ByteArray) -> Result<String> = { mimeType, data ->
            calls += mimeType to data
            if (calls.size == failAt) Result.failure(IllegalStateException("upload failed")) else Result.success("mxc://example.org/${calls.size}")
        }
    }

    @Test
    fun `pictures chosen together are sent as image messages marked as one batch`() = runTest {
        val sent = mutableListOf<JsonObject>()
        val uploads = Uploads()
        val room = FakeJoinedRoom(
            sendRawResult = { type, content ->
                assertThat(type).isEqualTo("m.room.message")
                sent += Json.parseToJsonElement(content).jsonObject
                Result.success(Unit)
            },
        )
        val sender = createDefaultMediaSender(room = room, uploadMedia = uploads.upload, newBatchId = { "batch-1" })

        val result = sender.sendBatch(listOf(anImage("a.jpg"), anImage("b.jpg"), anImage("c.jpg", withThumbnail = false)), "A caption", null)

        assertThat(result.isSuccess).isTrue()
        assertThat(sent).hasSize(3)
        sent.forEachIndexed { index, content ->
            val mark = content.getValue("io.intercom.gallery").jsonObject
            assertThat(mark.getValue("id").jsonPrimitive.content).isEqualTo("batch-1")
            assertThat(mark.getValue("index").jsonPrimitive.int).isEqualTo(index)
            assertThat(mark.getValue("count").jsonPrimitive.int).isEqualTo(3)
            assertThat(content.getValue("msgtype").jsonPrimitive.content).isEqualTo("m.image")
            assertThat(content).containsKey("url")
        }
        // The caption goes with the first picture only.
        assertThat(sent.map { it.getValue("body").jsonPrimitive.content }).containsExactly("A caption", "b.jpg", "c.jpg").inOrder()
        // Two files for a picture with a thumbnail, one for the picture without, each under its own type.
        assertThat(uploads.calls.map { it.first }).containsExactly(MimeTypes.Jpeg, MimeTypes.Jpeg, MimeTypes.Jpeg, MimeTypes.Jpeg, MimeTypes.Jpeg)
        assertThat(String(uploads.calls[1].second)).isEqualTo("a.jpg")
    }

    @Test
    fun `in an encrypted room the files of a batch are uploaded encrypted`() = runTest {
        val sent = mutableListOf<JsonObject>()
        val uploads = Uploads()
        val room = FakeJoinedRoom(
            sendRawResult = { _, content ->
                sent += Json.parseToJsonElement(content).jsonObject
                Result.success(Unit)
            },
        ).apply { givenRoomInfo(aRoomInfo(isEncrypted = true)) }
        val sender = createDefaultMediaSender(room = room, uploadMedia = uploads.upload)

        val result = sender.sendBatch(listOf(anImage("a.jpg"), anImage("b.jpg")), null, null)

        assertThat(result.isSuccess).isTrue()
        assertThat(sent).hasSize(2)
        assertThat(uploads.calls.map { it.first }.toSet()).containsExactly("application/octet-stream")
        // Nothing of the pictures goes out in the clear.
        assertThat(uploads.calls.map { String(it.second) }).containsNoneOf("a.jpg", "b.jpg")
        sent.forEach { content ->
            assertThat(content).doesNotContainKey("url")
            assertThat(content.getValue("file").jsonObject.getValue("url").jsonPrimitive.content).startsWith("mxc://")
            assertThat(content.getValue("info").jsonObject).containsKey("thumbnail_file")
        }
        assertThat(sent.map { it.getValue("io.intercom.gallery").jsonObject.getValue("id") }.toSet()).hasSize(1)
    }

    @Test
    fun `when a marked picture cannot be sent the rest goes the ordinary way`() = runTest {
        var raw = 0
        val ordinary = mutableListOf<String>()
        val uploads = Uploads().apply { failAt = 3 }
        val liveTimeline = FakeTimeline().apply {
            sendImageLambda = { file, _, _, _, _, _ ->
                ordinary += file.name
                Result.success(FakeMediaUploadHandler())
            }
        }
        val room = FakeJoinedRoom(
            liveTimeline = liveTimeline,
            sendRawResult = { _, _ ->
                raw++
                Result.success(Unit)
            },
        )
        val sender = createDefaultMediaSender(room = room, uploadMedia = uploads.upload)

        val result = sender.sendBatch(listOf(anImage("a.jpg"), anImage("b.jpg"), anImage("c.jpg")), null, null)

        assertThat(result.isSuccess).isTrue()
        assertThat(raw).isEqualTo(1)
        assertThat(ordinary).containsExactly("b.jpg", "c.jpg").inOrder()
    }

    @Test
    fun `a reply, a thread, files that are not pictures and a room of unknown encryption are sent the ordinary way`() = runTest {
        val ordinary = mutableListOf<String>()
        val captions = mutableListOf<String?>()
        val liveTimeline = FakeTimeline().apply {
            sendImageLambda = { file, _, _, caption, _, _ ->
                ordinary += file.name
                captions += caption
                Result.success(FakeMediaUploadHandler())
            }
        }
        val uploads = Uploads()
        val room = FakeJoinedRoom(liveTimeline = liveTimeline)
        val images = listOf(anImage("a.jpg"), anImage("b.jpg"))

        // A reply.
        createDefaultMediaSender(room = room, uploadMedia = uploads.upload).sendBatch(images, "Caption", AN_EVENT_ID)
        assertThat(ordinary).containsExactly("a.jpg", "b.jpg").inOrder()
        assertThat(captions).containsExactly("Caption", null).inOrder()

        // No uploader.
        ordinary.clear()
        createDefaultMediaSender(room = room).sendBatch(images, null, null)
        assertThat(ordinary).containsExactly("a.jpg", "b.jpg").inOrder()

        // Whether the room is encrypted is not known.
        ordinary.clear()
        room.givenRoomInfo(aRoomInfo().copy(isEncrypted = null))
        createDefaultMediaSender(room = room, uploadMedia = uploads.upload).sendBatch(images, null, null)
        assertThat(ordinary).containsExactly("a.jpg", "b.jpg").inOrder()
        assertThat(uploads.calls).isEmpty()
    }

    private fun createDefaultMediaSender(
        preProcessor: MediaPreProcessor = FakeMediaPreProcessor(),
        room: JoinedRoom = FakeJoinedRoom(),
        timelineMode: Timeline.Mode = Timeline.Mode.Live,
        mediaOptimizationConfigProvider: MediaOptimizationConfigProvider = MediaOptimizationConfigProvider { mediaOptimizationConfig },
        uploadMedia: (suspend (String, ByteArray) -> Result<String>)? = null,
        newBatchId: () -> String = { "a-batch" },
    ) = DefaultMediaSender(
        preProcessor = preProcessor,
        room = room,
        timelineMode = timelineMode,
        mediaOptimizationConfigProvider = mediaOptimizationConfigProvider,
        uploadMedia = uploadMedia,
        newBatchId = newBatchId,
    )
}
