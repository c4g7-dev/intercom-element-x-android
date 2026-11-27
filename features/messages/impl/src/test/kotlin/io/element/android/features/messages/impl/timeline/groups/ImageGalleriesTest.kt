/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.groups

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.aTimelineItemDebugInfo
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineItemReactions
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.TimelineItemThreadInfo
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGalleryContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.model.virtual.aTimelineItemDaySeparatorModel
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetails
import org.junit.Test

class ImageGalleriesTest {
    private val alice = UserId("@alice:example.org")
    private val bob = UserId("@bob:example.org")
    private val minute = 60_000L

    private fun aPicture(
        name: String,
        at: Long,
        sender: UserId = alice,
        caption: String? = null,
        reactions: Int = 0,
        groupPosition: TimelineItemGroupPosition = TimelineItemGroupPosition.None,
        threadInfo: TimelineItemThreadInfo? = null,
        inReplyTo: InReplyToDetails? = null,
        sendState: LocalEventSendState? = null,
        batch: String? = null,
    ): TimelineItem.Event = aTimelineItemEvent(
        eventId = EventId("\$$name"),
        senderId = sender,
        content = aTimelineItemImageContent(filename = "$name.jpg", caption = caption),
        groupPosition = groupPosition,
        threadInfo = threadInfo,
        inReplyTo = inReplyTo,
        sendState = sendState,
        timelineItemReactions = aTimelineItemReactions(count = reactions),
        debugInfo = aTimelineItemDebugInfo(
            originalJson = batch?.let { """{"content":{"msgtype":"m.image","io.intercom.gallery":{"id":"$it","index":0,"count":3}}}""" },
        ),
    ).copy(sentTimeMillis = at)

    private fun aText(at: Long, sender: UserId = alice) = aTimelineItemEvent(senderId = sender, content = aTimelineItemTextContent()).copy(sentTimeMillis = at)

    /** Folds [oldestFirst], which is given in the order of the conversation, and returns the result in that order too. */
    private fun fold(vararg oldestFirst: TimelineItem): List<TimelineItem> {
        return oldestFirst.toList().asReversed().foldImageRuns { galleryBatchIdOf(it.debugInfo.originalJson) }.asReversed()
    }

    private fun TimelineItem.gallery() = (this as TimelineItem.Event).content as TimelineItemGalleryContent
    private fun TimelineItem.names() = gallery().items.map { it.filename }

    @Test
    fun `pictures of one sender within two minutes of each other are one gallery, oldest first`() {
        val a = aPicture("a", at = 0, groupPosition = TimelineItemGroupPosition.First)
        val b = aPicture("b", at = minute, groupPosition = TimelineItemGroupPosition.Middle)
        val c = aPicture("c", at = 3 * minute, groupPosition = TimelineItemGroupPosition.Last)
        val result = fold(a, b, c)
        assertThat(result).hasSize(1)
        val item = result.single() as TimelineItem.Event
        assertThat(item.names()).containsExactly("a.jpg", "b.jpg", "c.jpg").inOrder()
        // The item is the oldest picture, with the gallery in place of its content.
        assertThat(item.id).isEqualTo(a.id)
        assertThat(item.eventId).isEqualTo(a.eventId)
        assertThat(item.gallery().foldedEventIds).containsExactly(a.eventId, b.eventId, c.eventId).inOrder()
        assertThat(item.foldedEventIds()).hasSize(3)
        // It stands where the whole run stood among the sender's messages.
        assertThat(item.groupPosition).isEqualTo(TimelineItemGroupPosition.None)
    }

    @Test
    fun `the gallery takes its place in the sender's run of messages from its first and last picture`() {
        fun position(oldest: TimelineItemGroupPosition, newest: TimelineItemGroupPosition): TimelineItemGroupPosition {
            val result = fold(aPicture("a", 0, groupPosition = oldest), aPicture("b", 1, groupPosition = newest))
            return (result.single() as TimelineItem.Event).groupPosition
        }
        assertThat(position(TimelineItemGroupPosition.First, TimelineItemGroupPosition.Middle)).isEqualTo(TimelineItemGroupPosition.First)
        assertThat(position(TimelineItemGroupPosition.Middle, TimelineItemGroupPosition.Last)).isEqualTo(TimelineItemGroupPosition.Last)
        assertThat(position(TimelineItemGroupPosition.Middle, TimelineItemGroupPosition.Middle)).isEqualTo(TimelineItemGroupPosition.Middle)
    }

    @Test
    fun `a gap of more than two minutes, another sender or a message between part two galleries`() {
        val gap = fold(aPicture("a", 0), aPicture("b", minute), aPicture("c", 3 * minute + 1), aPicture("d", 4 * minute))
        assertThat(gap).hasSize(2)
        assertThat(gap[0].names()).containsExactly("a.jpg", "b.jpg").inOrder()
        assertThat(gap[1].names()).containsExactly("c.jpg", "d.jpg").inOrder()

        val sender = fold(aPicture("a", 0), aPicture("b", 1, sender = bob), aPicture("c", 2, sender = bob))
        assertThat(sender).hasSize(2)
        assertThat((sender[0] as TimelineItem.Event).content).isInstanceOf(TimelineItemImageContent::class.java)
        assertThat(sender[1].names()).containsExactly("b.jpg", "c.jpg").inOrder()

        val between = fold(aPicture("a", 0), aText(1), aPicture("b", 2))
        assertThat(between).hasSize(3)
        val day = fold(aPicture("a", 0), TimelineItem.Virtual(UniqueId("day"), aTimelineItemDaySeparatorModel("Today")), aPicture("b", 2))
        assertThat(day).hasSize(3)
    }

    @Test
    fun `pictures of one batch are one gallery however far apart, and not with those of another`() {
        val result = fold(
            aPicture("a", 0, batch = "one"),
            aPicture("b", 10 * minute, batch = "one"),
            aPicture("c", 30 * minute, batch = "one"),
            aPicture("d", 60 * minute, batch = "two"),
            aPicture("e", 90 * minute),
        )
        assertThat(result).hasSize(3)
        assertThat(result[0].names()).containsExactly("a.jpg", "b.jpg", "c.jpg").inOrder()
    }

    @Test
    fun `the batch is read from the event only when the time does not decide`() {
        var asked = 0
        val items = listOf(aPicture("a", 0, batch = "one"), aPicture("b", minute, batch = "one"), aPicture("c", 2 * minute, batch = "one"))
        val result = items.asReversed().foldImageRuns {
            asked++
            "one"
        }
        assertThat(result).hasSize(1)
        assertThat(asked).isEqualTo(0)
    }

    @Test
    fun `a caption or reactions are only on the first picture of a gallery`() {
        val captions = fold(aPicture("a", 0, caption = "First"), aPicture("b", 1), aPicture("c", 2, caption = "Third"), aPicture("d", 3))
        assertThat(captions).hasSize(2)
        assertThat(captions[0].gallery().caption).isEqualTo("First")
        assertThat(captions[0].names()).containsExactly("a.jpg", "b.jpg").inOrder()
        assertThat(captions[1].gallery().caption).isEqualTo("Third")

        val reactions = fold(aPicture("a", 0, reactions = 2), aPicture("b", 1), aPicture("c", 2, reactions = 1), aPicture("d", 3))
        assertThat(reactions).hasSize(2)
        assertThat((reactions[0] as TimelineItem.Event).reactionsState.reactions).hasSize(2)
        assertThat(reactions[1].names()).containsExactly("c.jpg", "d.jpg").inOrder()
    }

    @Test
    fun `a failed send, the root of a thread and another thread stay out`() {
        val failed = fold(aPicture("a", 0), aPicture("b", 1, sendState = LocalEventSendState.Failed.Unknown("no")), aPicture("c", 2))
        assertThat(failed).hasSize(3)

        val thread = TimelineItemThreadInfo.ThreadResponse(ThreadId("\$root"))
        val threads = fold(aPicture("a", 0, threadInfo = thread), aPicture("b", 1, threadInfo = thread), aPicture("c", 2))
        assertThat(threads).hasSize(2)
        assertThat(threads[0].names()).containsExactly("a.jpg", "b.jpg").inOrder()
    }

    @Test
    fun `a picture still on its way makes the gallery be on its way`() {
        val sending = LocalEventSendState.Sending.Event
        val result = fold(aPicture("a", 0, sendState = LocalEventSendState.Sent(EventId("\$a"))), aPicture("b", 1, sendState = sending))
        assertThat((result.single() as TimelineItem.Event).localSendState).isEqualTo(sending)
    }

    @Test
    fun `one picture alone and a timeline without pictures are left as they are`() {
        val single = listOf(aText(0), aPicture("a", 1), aText(2))
        assertThat(single.foldImageRuns { null }).isSameInstanceAs(single)
        val a = aPicture("a", 0)
        val b = aPicture("b", 10 * minute)
        assertThat(fold(a, b)).containsExactly(a, b).inOrder()
    }

    @Test
    fun `the batch id is read from the event's JSON`() {
        assertThat(galleryBatchIdOf(null)).isNull()
        assertThat(galleryBatchIdOf("""{"content":{"msgtype":"m.image"}}""")).isNull()
        assertThat(galleryBatchIdOf("""{"content":{"io.intercom.gallery":{"index":1,"id":"abc-1","count":2}}}""")).isEqualTo("abc-1")
        assertThat(galleryBatchIdOf("""{"content":{"io.intercom.gallery": { "id" : "abc-2" }}}""")).isEqualTo("abc-2")
        assertThat(galleryBatchIdOf("""{"content":{"io.intercom.gallery":{"id":7}}}""")).isNull()
        assertThat(galleryBatchIdOf("""{"content":{"io.intercom.gallery":{"id":""}}}""")).isNull()
        // The body of a message is not the mark.
        assertThat(galleryBatchIdOf("""{"content":{"body":"io.intercom.gallery id x"}}""")).isNull()
    }
}
