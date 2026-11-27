/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * InterCom: where the composer's buttons stand next to its text.
 *
 * The field grows upwards, so everything in the row is aligned to its bottom, and the buttons are lifted until
 * their centre is on the centre line of the last line of text. Upstream lifts them by a fixed 5 dp, which is right
 * for the default font size only: with a larger font the line is taller, its centre higher, and the buttons sit
 * below it, at the bottom of the field.
 *
 * The line's height is not known to the layout (the text is an Android view, and the font is the device's), so
 * the height of the field with one line of text is measured: the buttons are centred on that field, and keep that
 * distance to the bottom when it grows. Until a one-line field was measured (a draft of several lines), the height
 * is estimated from the font size.
 */
internal object ComposerMetrics {
    /** The height of one line of the composer's text at the default font size. */
    val textLineHeight = 23.dp

    /** A button beside the field, and its touch target. */
    val sideButtonSize = 48.dp

    /** What the field's box has above and below the text input, and the input above and below its text. */
    private val boxPadding = 9.dp
    private val inputPadding = 11.dp
    private val boxBottomPadding = 4.dp
    private val fieldBottomPadding = 8.dp
    private val minFieldHeight = 42.dp

    /**
     * The height the field is expected to have with one line of text: 43 dp at the default font size. An
     * estimate: the font of the device may have taller lines than the size says, so the layout takes the height it
     * measures instead where it can, see [isOneLine].
     */
    fun estimatedFieldHeight(fontScale: Float): Dp {
        return (boxPadding + inputPadding + textLineHeight * fontScale.coerceAtLeast(MIN_FONT_SCALE)).coerceAtLeast(minFieldHeight)
    }

    /**
     * Whether a field of the height [measured] holds one line of text: it is not much taller than [estimated].
     * With two lines it is taller by a whole line, half of its height again.
     */
    fun isOneLine(measured: Dp, estimated: Dp): Boolean = measured > 0.dp && measured <= estimated * ONE_LINE_TOLERANCE

    /**
     * The padding below a button beside a field that is [fieldHeight] tall with one line of text, which puts the
     * button's centre on the centre of that line, and with more lines on the centre of the last: 5 dp at the
     * default font size.
     */
    fun sideButtonBottomPadding(fieldHeight: Dp): Dp = (fieldBottomPadding + (fieldHeight - sideButtonSize) / 2).coerceAtLeast(0.dp)

    /** The height of the text input with one line of text, in a field that is [fieldHeight] tall then: 34 dp. */
    fun inputLineHeight(fieldHeight: Dp): Dp = (fieldHeight - boxPadding).coerceAtLeast(0.dp)

    /** The box of the emoji button inside the field: one line of the text input at the default font size. */
    val emojiButtonBoxHeight = 34.dp

    /**
     * The padding below the emoji button's box inside a field that is [fieldHeight] tall with one line of text,
     * which puts the box on the centre of the last line: 4 dp at the default font size.
     */
    fun emojiButtonBottomPadding(fieldHeight: Dp): Dp {
        return boxBottomPadding + ((inputLineHeight(fieldHeight) - emojiButtonBoxHeight) / 2).coerceAtLeast(0.dp)
    }

    private const val MIN_FONT_SCALE = 0.85f
    private const val ONE_LINE_TOLERANCE = 1.3f
}

/**
 * InterCom: the height of the composer's field with one line of text: [estimated] until a field of one line was
 * measured, then the smallest such height that was measured.
 */
@Stable
internal class ComposerFieldHeight(private val estimated: Dp) {
    var value: Dp by mutableStateOf(estimated)
        private set
    private var isMeasured = false

    fun onMeasured(height: Dp) {
        if (!ComposerMetrics.isOneLine(height, estimated)) return
        if (!isMeasured || height < value) {
            isMeasured = true
            value = height
        }
    }
}
