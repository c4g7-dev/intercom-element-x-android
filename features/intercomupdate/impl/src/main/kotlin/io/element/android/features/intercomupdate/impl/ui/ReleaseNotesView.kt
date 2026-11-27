/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.intercomupdate.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.intercomupdate.impl.R
import io.element.android.features.intercomupdate.impl.logic.NotesBlock
import io.element.android.features.intercomupdate.impl.logic.NotesSpan
import io.element.android.features.intercomupdate.impl.logic.ReleaseNote
import io.element.android.features.intercomupdate.impl.logic.ReleaseNotesText
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.ImmutableList

/**
 * The notes of [notes], newest first, as items of a list: each version's name, when there is more than one, and
 * its notes as text and lists. Nothing in them is interpreted beyond that, see [ReleaseNotesText].
 */
internal fun LazyListScope.releaseNotes(notes: ImmutableList<ReleaseNote>) {
    notes.forEach { note ->
        item(key = "notes-${note.versionCode}") {
            ReleaseNoteView(note = note, showVersion = notes.size > 1)
        }
    }
}

@Composable
internal fun ReleaseNoteView(
    note: ReleaseNote,
    showVersion: Boolean,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(note.notes) { ReleaseNotesText.parse(note.notes) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showVersion) {
            Text(
                text = stringResource(R.string.screen_intercom_update_whats_new_version, note.versionName),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
        }
        blocks.forEach { block ->
            when (block) {
                is NotesBlock.Heading -> Text(
                    text = block.spans.toAnnotatedString(),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                is NotesBlock.Paragraph -> Text(
                    text = block.spans.toAnnotatedString(),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
                is NotesBlock.Bullet -> Row(modifier = Modifier.padding(start = (block.level * 12).dp)) {
                    Text(
                        text = "•",
                        modifier = Modifier.padding(end = 8.dp),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                    Text(
                        text = block.spans.toAnnotatedString(),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}

private fun List<NotesSpan>.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    this@toAnnotatedString.forEach { span ->
        when {
            span.bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
            span.code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(span.text) }
            else -> append(span.text)
        }
    }
}
