/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.groups

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.TimelineItemReadReceipts
import io.element.android.features.messages.impl.timeline.model.TimelineItemThreadInfo
import io.element.android.features.messages.impl.timeline.model.event.GalleryItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGalleryContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.collections.immutable.toImmutableList

/** InterCom: pictures sent further apart than this are not one gallery, unless they are of one batch. */
internal const val GALLERY_MAX_GAP_MILLIS = 2 * 60 * 1000L

/**
 * The content field that marks the pictures sent together, `{ id, index, count }`, written by InterCom on the
 * desktop and on Android (ImageBatch in libraries/mediaupload). Other clients ignore it.
 */
internal const val GALLERY_BATCH_FIELD = "io.intercom.gallery"

private val batchIdPattern = Regex(""""io\.intercom\.gallery"\s*:\s*\{[^{}]*?"id"\s*:\s*"((?:[^"\\]|\\.){1,200})"""")

/** The batch a picture message says it was sent in, read from the event's JSON, or null. */
internal fun galleryBatchIdOf(eventJson: String?): String? {
    if (eventJson == null || !eventJson.contains(GALLERY_BATCH_FIELD)) return null
    return batchIdPattern.find(eventJson)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
}

/**
 * InterCom: shows the pictures somebody sent together as one gallery, as the desktop app does.
 *
 * A run of picture messages of one sender with nothing shown between them, each within two minutes of the one
 * before or of the same batch ([batchIdOf]), becomes one item with a [TimelineItemGalleryContent], drawn as the
 * grid Element X has for `m.gallery` messages and opened in the viewer that pages through its pictures. Every
 * picture stays the message it is: the item is the oldest one with the gallery in place of its picture, so what is
 * done to the item (a reaction, a reply, removing it) is done to that picture, and when it goes, the others are
 * shown without it.
 *
 * What keeps a picture out of a gallery, as on the desktop: a reply, a caption (only the first picture of a gallery
 * may have one, it is text), and here also reactions (only the first picture's are shown, so a later picture with
 * reactions starts a gallery of its own), a failed send (it needs its own "try again") and the root of a thread.
 *
 * The list is ordered newest first, like everything the grouper handles. [batchIdOf] is asked only for pictures
 * that are too far apart in time, as it reads the event's JSON.
 */
internal fun List<TimelineItem>.foldImageRuns(batchIdOf: (TimelineItem.Event) -> String?): List<TimelineItem> {
    if (count { it.isGalleryCandidate() } < 2) return this
    val result = ArrayList<TimelineItem>(size)
    // Newest first, as the list is.
    val run = ArrayList<TimelineItem.Event>()

    fun flushRun() {
        when (run.size) {
            0 -> Unit
            1 -> result.add(run.first())
            else -> result.add(run.toGalleryItem())
        }
        run.clear()
    }

    for (item in this) {
        if (item !is TimelineItem.Event || !item.isGalleryCandidate()) {
            flushRun()
            result.add(item)
            continue
        }
        // `item` is older than the pictures of the run: can the run's oldest picture go on after it?
        val next = run.lastOrNull()
        if (next != null && !next.continues(item, batchIdOf)) flushRun()
        run.add(item)
    }
    flushRun()
    return result
}

private fun TimelineItem.isGalleryCandidate(): Boolean {
    return this is TimelineItem.Event &&
        content is TimelineItemImageContent &&
        inReplyTo == null &&
        !failedToSend &&
        threadInfo !is TimelineItemThreadInfo.ThreadRoot
}

/** Whether this picture goes on with the gallery of [previous], the picture right before it. */
private fun TimelineItem.Event.continues(previous: TimelineItem.Event, batchIdOf: (TimelineItem.Event) -> String?): Boolean {
    if (senderId != previous.senderId) return false
    if (threadInfo != previous.threadInfo) return false
    // Text and reactions belong to one picture, and the gallery shows only those of its first.
    if ((content as? TimelineItemImageContent)?.showCaption == true) return false
    if (reactionsState.reactions.isNotEmpty()) return false
    val isNear = kotlin.math.abs(sentTimeMillis - previous.sentTimeMillis) <= GALLERY_MAX_GAP_MILLIS && sentDate == previous.sentDate
    if (isNear) return true
    val batch = batchIdOf(this) ?: return false
    return batch == batchIdOf(previous)
}

/** One item for a run of pictures, newest first. */
private fun List<TimelineItem.Event>.toGalleryItem(): TimelineItem.Event {
    val newest = first()
    val oldest = last()
    val oldestFirst = asReversed()
    val firstContent = oldest.content as TimelineItemImageContent
    return oldest.copy(
        content = TimelineItemGalleryContent(
            body = firstContent.filename,
            caption = firstContent.caption,
            formattedCaption = firstContent.formattedCaption,
            htmlCaption = firstContent.htmlCaption,
            captionTree = firstContent.captionTree,
            isEdited = firstContent.isEdited,
            items = oldestFirst.map { (it.content as TimelineItemImageContent).toGalleryItem() }.toImmutableList(),
            foldedEventIds = oldestFirst.mapNotNull { it.eventId }.toImmutableList(),
        ),
        groupPosition = groupPositionOf(oldest.groupPosition, newest.groupPosition),
        // Whoever has read the newest picture has read the gallery.
        readReceiptState = TimelineItemReadReceipts(flatMap { it.readReceiptState.receipts }.distinctBy { it.userId }.toImmutableList()),
        // While any picture is on its way, the gallery is.
        localSendState = firstNotNullOfOrNull { it.localSendState },
    )
}

/** The position of an item that stands for a run from an item at [oldest] to one at [newest]. */
private fun groupPositionOf(oldest: TimelineItemGroupPosition, newest: TimelineItemGroupPosition): TimelineItemGroupPosition {
    val starts = oldest == TimelineItemGroupPosition.First || oldest == TimelineItemGroupPosition.None
    val ends = newest == TimelineItemGroupPosition.Last || newest == TimelineItemGroupPosition.None
    return when {
        starts && ends -> TimelineItemGroupPosition.None
        starts -> TimelineItemGroupPosition.First
        ends -> TimelineItemGroupPosition.Last
        else -> TimelineItemGroupPosition.Middle
    }
}

private fun TimelineItemImageContent.toGalleryItem() = GalleryItem(
    filename = filename,
    mimeType = mimeType,
    mediaSource = mediaSource,
    type = GalleryItem.Type.Image,
    thumbnailSource = thumbnailSource,
    width = width,
    height = height,
    thumbnailWidth = thumbnailWidth,
    thumbnailHeight = thumbnailHeight,
    blurhash = blurhash,
)

/** The events a gallery made of several picture messages stands for, oldest first; empty for any other item. */
internal fun TimelineItem.Event.foldedEventIds(): List<EventId> = (content as? TimelineItemGalleryContent)?.foldedEventIds.orEmpty()
