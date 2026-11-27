/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.logic

/**
 * Release notes as the app draws them: text and lists. The notes are the Markdown of a GitHub release, which
 * anybody with access to the repository writes, so nothing in them is interpreted beyond this: HTML is taken out,
 * pictures are left out (nothing is loaded from the network), and a link is its text.
 */
sealed interface NotesBlock {
    val spans: List<NotesSpan>

    data class Heading(override val spans: List<NotesSpan>) : NotesBlock
    data class Paragraph(override val spans: List<NotesSpan>) : NotesBlock

    /** [level] 0 for a top-level item. */
    data class Bullet(val level: Int, override val spans: List<NotesSpan>) : NotesBlock
}

data class NotesSpan(val text: String, val bold: Boolean = false, val code: Boolean = false)

object ReleaseNotesText {
    private val image = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val link = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val html = Regex("""<[^>\n]{0,2000}>""")
    private val heading = Regex("""^#{1,6}\s+(.*)$""")
    private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val tableRule = Regex("""^\|?[\s:|-]+\|?$""")
    private val inline = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")

    /** At most [maxBlocks] blocks, so that a long changelog stays a dialog. */
    fun parse(markdown: String, maxBlocks: Int = 60): List<NotesBlock> {
        val blocks = mutableListOf<NotesBlock>()
        val pending = StringBuilder()
        var pendingBullet: Int? = null

        fun flush() {
            val text = pending.toString().trim()
            pending.clear()
            val level = pendingBullet
            pendingBullet = null
            if (text.isEmpty()) return
            blocks += if (level != null) NotesBlock.Bullet(level, spans(text)) else NotesBlock.Paragraph(spans(text))
        }

        for (raw in markdown.replace("\r\n", "\n").split('\n')) {
            val line = clean(raw)
            val trimmed = line.trim()
            // A line that was a picture, HTML or the rule of a table leaves nothing to show.
            if (trimmed.isEmpty() || trimmed.contains('-') && tableRule.matches(trimmed)) {
                flush()
                continue
            }
            val headingMatch = heading.matchEntire(trimmed)
            val bulletMatch = bullet.matchEntire(line)
            when {
                headingMatch != null -> {
                    flush()
                    blocks += NotesBlock.Heading(spans(headingMatch.groupValues[1]))
                }
                bulletMatch != null -> {
                    flush()
                    pendingBullet = (bulletMatch.groupValues[1].length / 2).coerceAtMost(2)
                    pending.append(bulletMatch.groupValues[2])
                }
                else -> {
                    // A changelog is wrapped by hand: a line goes on with the next.
                    if (pending.isNotEmpty()) pending.append(' ')
                    pending.append(trimmed.trim('|').trim().replace(Regex("""\s*\|\s*"""), " · "))
                }
            }
        }
        flush()
        return blocks.filter { block -> block.spans.any { it.text.isNotBlank() } }.take(maxBlocks)
    }

    private fun clean(line: String): String = line.replace(image, "").replace(html, "").replace(link, "$1").trimEnd()

    private fun spans(text: String): List<NotesSpan> {
        val result = mutableListOf<NotesSpan>()
        var index = 0
        for (match in inline.findAll(text)) {
            if (match.range.first > index) result += NotesSpan(text.substring(index, match.range.first))
            val bold = match.groupValues[1]
            result += if (bold.isNotEmpty()) NotesSpan(bold.replace("`", ""), bold = true) else NotesSpan(match.groupValues[2], code = true)
            index = match.range.last + 1
        }
        if (index < text.length) result += NotesSpan(text.substring(index))
        return result
    }
}
