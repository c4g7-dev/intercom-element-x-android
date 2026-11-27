/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReleaseNotesTextTest {
    private fun NotesBlock.text() = spans.joinToString("") { it.text }

    @Test
    fun `a changelog section becomes paragraphs and list items, hand-wrapped lines joined`() {
        val blocks = ReleaseNotesText.parse(
            """
            `android-v26.11.1-intercom.14`, based on Element X Android 26.11.1. Call library
            0.1.2-intercom.6.

            - **Deafen** in calls: nobody is heard,
              and the microphone is muted with it.
            - Pictures sent together are one gallery.
              - Up to nine are shown.

            ### Known limitations
            Text after a heading.
            """.trimIndent()
        )
        assertThat(blocks.map { it::class }).containsExactly(
            NotesBlock.Paragraph::class,
            NotesBlock.Bullet::class,
            NotesBlock.Bullet::class,
            NotesBlock.Bullet::class,
            NotesBlock.Heading::class,
            NotesBlock.Paragraph::class,
        ).inOrder()
        assertThat(blocks[0].text()).isEqualTo("android-v26.11.1-intercom.14, based on Element X Android 26.11.1. Call library 0.1.2-intercom.6.")
        assertThat(blocks[0].spans.first()).isEqualTo(NotesSpan("android-v26.11.1-intercom.14", code = true))
        assertThat(blocks[1].text()).isEqualTo("Deafen in calls: nobody is heard, and the microphone is muted with it.")
        assertThat(blocks[1].spans.first()).isEqualTo(NotesSpan("Deafen", bold = true))
        assertThat((blocks[2] as NotesBlock.Bullet).level).isEqualTo(0)
        assertThat((blocks[3] as NotesBlock.Bullet).level).isEqualTo(1)
        assertThat(blocks[4].text()).isEqualTo("Known limitations")
    }

    @Test
    fun `pictures and HTML are left out, and a link is its text`() {
        val blocks = ReleaseNotesText.parse(
            """
            | Before | After |
            | --- | --- |
            | ![old](https://example.org/a.png) | ![new](https://example.org/b.png) |

            <p><img src="https://example.org/track.png" width="460" alt="x"><br><sub>A caption</sub></p>

            See [the documentation](https://example.org/docs) <script>alert(1)</script>for more.
            """.trimIndent()
        )
        val text = blocks.joinToString("\n") { it.text() }
        assertThat(text).doesNotContain("example.org")
        assertThat(text).doesNotContain("<")
        assertThat(text).doesNotContain("![")
        assertThat(text).contains("Before · After")
        assertThat(text).contains("A caption")
        assertThat(text).contains("See the documentation alert(1)for more.")
    }

    @Test
    fun `plain text of an update file stays as it is, and long notes are cut`() {
        val blocks = ReleaseNotesText.parse("• One thing.\n\n• Another thing.")
        assertThat(blocks.map { it.text() }).containsExactly("• One thing.", "• Another thing.").inOrder()
        val long = (1..200).joinToString("\n") { "- item $it" }
        assertThat(ReleaseNotesText.parse(long, maxBlocks = 10)).hasSize(10)
        assertThat(ReleaseNotesText.parse("")).isEmpty()
    }
}
