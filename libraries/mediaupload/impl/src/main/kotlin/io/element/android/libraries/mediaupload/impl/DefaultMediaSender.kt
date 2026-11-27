/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.androidutils.hash.hash
import io.element.android.libraries.core.extensions.flatMap
import io.element.android.libraries.core.extensions.flatMapCatching
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaUploadHandler
import io.element.android.libraries.matrix.api.room.CreateTimelineParams
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProviderFactory
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import io.element.android.libraries.mediaupload.api.MediaSender
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.mediaupload.api.MediaSenderRoomFactory
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import io.element.android.libraries.mediaupload.api.toGalleryItemInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ContributesBinding(RoomScope::class)
class DefaultMediaSenderFactory(
    private val preProcessor: MediaPreProcessor,
    private val room: JoinedRoom,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
    private val client: MatrixClient,
) : MediaSenderFactory {
    override fun create(
        timelineMode: Timeline.Mode,
    ): MediaSender {
        return DefaultMediaSender(
            preProcessor = preProcessor,
            room = room,
            timelineMode = timelineMode,
            mediaOptimizationConfigProvider = mediaOptimizationConfigProvider,
            uploadMedia = client::uploadMedia,
        )
    }
}

@ContributesBinding(AppScope::class)
class DefaultMediaSenderRoomFactory(
    private val preProcessor: MediaPreProcessor,
    private val mediaOptimizationConfigProviderFactory: MediaOptimizationConfigProviderFactory,
) : MediaSenderRoomFactory {
    override fun create(
        client: MatrixClient,
        room: JoinedRoom,
    ): MediaSender {
        return DefaultMediaSender(
            preProcessor = preProcessor,
            room = room,
            timelineMode = Timeline.Mode.Live,
            mediaOptimizationConfigProvider = mediaOptimizationConfigProviderFactory.create(client),
            uploadMedia = client::uploadMedia,
        )
    }
}

class DefaultMediaSender(
    private val preProcessor: MediaPreProcessor,
    private val room: JoinedRoom,
    private val timelineMode: Timeline.Mode,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
    /** InterCom: uploads a file and returns its `mxc://` address. Without it, a batch of pictures is sent unmarked. */
    private val uploadMedia: (suspend (mimeType: String, data: ByteArray) -> Result<String>)? = null,
    private val newBatchId: () -> String = { UUID.randomUUID().toString() },
) : MediaSender {
    private val ongoingUploadJobs = ConcurrentHashMap<Job.Key, MediaUploadHandler>()
    val hasOngoingMediaUploads get() = ongoingUploadJobs.isNotEmpty()

    override suspend fun preProcessMedia(
        uri: Uri,
        mimeType: String,
        mediaOptimizationConfig: MediaOptimizationConfig,
    ): Result<MediaUploadInfo> {
        Timber.d("Pre-processing media | uri: ${mediaId(uri)} | mimeType: $mimeType")
        return preProcessor
            .process(
                uri = uri,
                mimeType = mimeType,
                deleteOriginal = false,
                mediaOptimizationConfig = mediaOptimizationConfig,
            )
    }

    override suspend fun sendPreProcessedMedia(
        mediaUploadInfo: MediaUploadInfo,
        caption: String?,
        formattedCaption: String?,
        inReplyToEventId: EventId?,
    ): Result<Unit> {
        val mediaLogId = mediaId(mediaUploadInfo.file)
        return withTimeline { timeline ->
            Timber.d("Started sending media $mediaLogId using timeline: ${timeline.mode}")
            timeline.sendMedia(
                uploadInfo = mediaUploadInfo,
                caption = caption,
                formattedCaption = formattedCaption,
                inReplyToEventId = inReplyToEventId,
            )
        }
            .handleSendResult(mediaLogId)
    }

    override suspend fun sendMedia(
        uri: Uri,
        mimeType: String,
        caption: String?,
        formattedCaption: String?,
        inReplyToEventId: EventId?,
        mediaOptimizationConfig: MediaOptimizationConfig,
    ): Result<Unit> {
        return preProcessor
            .process(
                uri = uri,
                mimeType = mimeType,
                deleteOriginal = false,
                mediaOptimizationConfig = mediaOptimizationConfig,
            )
            .flatMapCatching { info ->
                withTimeline { timeline ->
                    timeline.sendMedia(
                        uploadInfo = info,
                        caption = caption,
                        formattedCaption = formattedCaption,
                        inReplyToEventId = inReplyToEventId,
                    )
                }
            }
            .handleSendResult(mediaId(uri))
    }

    override suspend fun sendVoiceMessage(
        uri: Uri,
        mimeType: String,
        waveForm: List<Float>,
        inReplyToEventId: EventId?,
    ): Result<Unit> {
        return preProcessor
            .process(
                uri = uri,
                mimeType = mimeType,
                deleteOriginal = true,
                mediaOptimizationConfig = mediaOptimizationConfigProvider.get(),
            )
            .flatMapCatching { info ->
                val audioInfo = (info as MediaUploadInfo.Audio).audioInfo
                val newInfo = MediaUploadInfo.VoiceMessage(
                    file = info.file,
                    audioInfo = audioInfo,
                    waveform = waveForm,
                )
                withTimeline { timeline ->
                    timeline.sendMedia(
                        uploadInfo = newInfo,
                        caption = null,
                        formattedCaption = null,
                        inReplyToEventId = inReplyToEventId,
                    )
                }
            }
            .handleSendResult(mediaId(uri))
    }

    override suspend fun sendGallery(
        mediaUploadInfos: List<MediaUploadInfo>,
        caption: String?,
        formattedCaption: String?,
        inReplyToEventId: EventId?,
    ): Result<Unit> {
        val galleryLogId = "gallery[${mediaUploadInfos.size} items]"
        Timber.d("Sending $galleryLogId")
        return withTimeline { timeline ->
            val galleryItems = mediaUploadInfos.map { it.toGalleryItemInfo() }
            timeline.sendGallery(
                items = galleryItems,
                caption = caption,
                formattedCaption = formattedCaption,
                inReplyToEventId = inReplyToEventId,
            )
                .flatMapCatching { uploadHandler ->
                    uploadHandler.await()
                }
        }
            .handleSendResult(galleryLogId)
    }

    override suspend fun sendBatch(
        mediaUploadInfos: List<MediaUploadInfo>,
        caption: String?,
        inReplyToEventId: EventId?,
    ): Result<Unit> {
        val images = mediaUploadInfos.filterIsInstance<MediaUploadInfo.Image>()
        // The mark is for pictures in the room's own timeline: a reply stays a message of its own on the desktop
        // too, and a thread's messages carry a relation that only the SDK's senders write.
        val canMark = uploadMedia != null &&
            images.size == mediaUploadInfos.size &&
            images.size > 1 &&
            inReplyToEventId == null &&
            timelineMode == Timeline.Mode.Live
        // Whether the room is encrypted must be known: a file sent in the clear into an encrypted room would be a leak.
        val isEncrypted = if (canMark) room.info().isEncrypted else null
        var sent = 0
        if (canMark && isEncrypted != null) {
            val batchId = newBatchId()
            Timber.d("Sending a batch of ${images.size} pictures, encrypted: $isEncrypted")
            for ((index, image) in images.withIndex()) {
                val result = sendMarkedImage(batchId, index, images.size, image, caption.takeIf { index == 0 }, isEncrypted)
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    if (error is CancellationException) throw error
                    // What is left goes the ordinary way, which has its own retries; desktop joins it by time.
                    Timber.w(error, "Picture ${index + 1} of the batch was not sent marked, sending the rest unmarked")
                    break
                }
                sent++
            }
        }
        for ((index, info) in mediaUploadInfos.withIndex().drop(sent)) {
            val result = sendPreProcessedMedia(
                mediaUploadInfo = info,
                caption = caption.takeIf { index == 0 },
                formattedCaption = null,
                inReplyToEventId = inReplyToEventId.takeIf { index == 0 },
            )
            if (result.isFailure) return result
        }
        return Result.success(Unit)
    }

    private suspend fun sendMarkedImage(
        batchId: String,
        index: Int,
        count: Int,
        image: MediaUploadInfo.Image,
        caption: String?,
        encrypt: Boolean,
    ): Result<Unit> = runCatchingExceptions {
        val upload = requireNotNull(uploadMedia)
        suspend fun uploadFile(file: File, mimeType: String?): ImageBatch.Uploaded {
            val bytes = file.readBytes()
            return if (encrypt) {
                val encrypted = EncryptedAttachment.encrypt(bytes)
                ImageBatch.Uploaded(upload("application/octet-stream", encrypted.data).getOrThrow(), encrypted)
            } else {
                ImageBatch.Uploaded(upload(mimeType ?: "application/octet-stream", bytes).getOrThrow(), null)
            }
        }
        val thumbnail = image.thumbnailFile?.let { uploadFile(it, image.imageInfo.thumbnailInfo?.mimetype) }
        val uploaded = uploadFile(image.file, image.imageInfo.mimetype)
        val content = ImageBatch.content(
            batchId = batchId,
            index = index,
            count = count,
            filename = image.file.name,
            caption = caption,
            info = image.imageInfo,
            image = uploaded,
            thumbnail = thumbnail,
        )
        room.sendRaw(ImageBatch.EVENT_TYPE, content.toString()).getOrThrow()
    }

    private fun Result<Unit>.handleSendResult(mediaId: String) = this
        .onFailure { error ->
            val job = ongoingUploadJobs.remove(Job)
            Timber.e(error, "Sending media $mediaId failed. Removing ongoing upload job. Total: ${ongoingUploadJobs.size}")
            if (error !is CancellationException) {
                job?.cancel()
            }
        }
        .onSuccess {
            Timber.d("Sent media $mediaId successfully. Removing ongoing upload job. Total: ${ongoingUploadJobs.size}")
            ongoingUploadJobs.remove(Job)
        }

    private suspend fun Timeline.sendMedia(
        uploadInfo: MediaUploadInfo,
        caption: String?,
        formattedCaption: String?,
        inReplyToEventId: EventId?,
    ): Result<Unit> {
        val handler = when (uploadInfo) {
            is MediaUploadInfo.Image -> {
                sendImage(
                    file = uploadInfo.file,
                    thumbnailFile = uploadInfo.thumbnailFile,
                    imageInfo = uploadInfo.imageInfo,
                    caption = caption,
                    formattedCaption = formattedCaption,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            is MediaUploadInfo.Video -> {
                sendVideo(
                    file = uploadInfo.file,
                    thumbnailFile = uploadInfo.thumbnailFile,
                    videoInfo = uploadInfo.videoInfo,
                    caption = caption,
                    formattedCaption = formattedCaption,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            is MediaUploadInfo.Audio -> {
                sendAudio(
                    file = uploadInfo.file,
                    audioInfo = uploadInfo.audioInfo,
                    caption = caption,
                    formattedCaption = formattedCaption,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            is MediaUploadInfo.VoiceMessage -> {
                sendVoiceMessage(
                    file = uploadInfo.file,
                    audioInfo = uploadInfo.audioInfo,
                    waveform = uploadInfo.waveform,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            is MediaUploadInfo.AnyFile -> {
                sendFile(
                    file = uploadInfo.file,
                    fileInfo = uploadInfo.fileInfo,
                    caption = caption,
                    formattedCaption = formattedCaption,
                    inReplyToEventId = inReplyToEventId,
                )
            }
        }

        // We handle the cancellations here manually, so we suppress the warning
        @Suppress("RunCatchingNotAllowed")
        return handler
            .mapCatching { uploadHandler ->
                Timber.d("Added ongoing upload job, total: ${ongoingUploadJobs.size + 1}")
                ongoingUploadJobs[Job] = uploadHandler
                uploadHandler.await()
            }
    }

    /**
     * Invokes [block] with the [Timeline] matching [timelineMode].
     *
     * For a thread, a dedicated timeline is created and closed once [block] returns, since such a
     * timeline owns SDK resources. [block] must then perform the whole sending, including waiting
     * for the media upload to complete. For the other modes, the live timeline is used, and is not
     * closed here since it is owned by its room.
     */
    private suspend fun <T> withTimeline(block: suspend (Timeline) -> Result<T>): Result<T> {
        return when (timelineMode) {
            is Timeline.Mode.Thread -> {
                room.createTimeline(CreateTimelineParams.Threaded(threadRootEventId = timelineMode.threadRootId))
                    .flatMap { threadedTimeline ->
                        threadedTimeline.use { block(it) }
                    }
            }
            else -> block(room.liveTimeline)
        }
    }

    /**
     * Clean up any temporary files or resources used during the media processing.
     */
    override fun cleanUp() = preProcessor.cleanUp()
}

private fun mediaId(uri: Uri?): String = uri?.path.orEmpty().hash()
private fun mediaId(file: File): String = file.path.orEmpty().hash()
