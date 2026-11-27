/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ComposerMetricsTest {
    @Test
    fun `at the default font size the buttons are where upstream puts them, on the centre of the one line`() {
        val field = ComposerMetrics.estimatedFieldHeight(1f)
        assertThat(field).isEqualTo(43.dp)
        assertThat(ComposerMetrics.inputLineHeight(field)).isEqualTo(34.dp)
        assertThat(ComposerMetrics.emojiButtonBottomPadding(field)).isEqualTo(4.dp)
        // In a field whose line is 10 dp taller, the emoji button is 5 dp higher: on the line's centre.
        assertThat(ComposerMetrics.emojiButtonBottomPadding(field + 10.dp)).isEqualTo(9.dp)
        // Upstream's 5 dp, to half a dp.
        assertThat(ComposerMetrics.sideButtonBottomPadding(field).value).isWithin(0.5f).of(5f)
    }

    @Test
    fun `the buttons are centred on a field of one line, however tall its line is`() {
        for (field in listOf(43.dp, 48.dp, 52.dp, 60.dp, 75.dp)) {
            val padding = ComposerMetrics.sideButtonBottomPadding(field)
            // 8 dp are below the field: the centre of the button is on the centre of the field.
            assertThat((padding + ComposerMetrics.sideButtonSize / 2).value).isWithin(0.01f).of((8.dp + field / 2).value)
        }
        // A taller line lifts them, which a fixed padding does not.
        assertThat(ComposerMetrics.sideButtonBottomPadding(56.dp)).isGreaterThan(ComposerMetrics.sideButtonBottomPadding(43.dp))
    }

    @Test
    fun `a larger font is expected to give a taller field`() {
        assertThat(ComposerMetrics.estimatedFieldHeight(1.5f)).isEqualTo(54.5.dp)
        assertThat(ComposerMetrics.estimatedFieldHeight(0.5f)).isEqualTo(42.dp)
    }

    @Test
    fun `only a field of one line is taken as the measure`() {
        val estimated = ComposerMetrics.estimatedFieldHeight(1f)
        assertThat(ComposerMetrics.isOneLine(43.dp, estimated)).isTrue()
        // A font with taller lines than its size says.
        assertThat(ComposerMetrics.isOneLine(52.dp, estimated)).isTrue()
        // Two lines, three lines, nothing.
        assertThat(ComposerMetrics.isOneLine(66.dp, estimated)).isFalse()
        assertThat(ComposerMetrics.isOneLine(89.dp, estimated)).isFalse()
        assertThat(ComposerMetrics.isOneLine(0.dp, estimated)).isFalse()
    }

    @Test
    fun `the buttons keep their touch target and never go below the row`() {
        assertThat(ComposerMetrics.sideButtonSize).isEqualTo(48.dp)
        assertThat(ComposerMetrics.sideButtonBottomPadding(20.dp)).isAtLeast(0.dp)
    }

    @Test
    fun `the field's height is the estimate until a field of one line was measured, then the smallest one measured`() {
        val fieldHeight = ComposerFieldHeight(estimated = 43.dp)
        assertThat(fieldHeight.value).isEqualTo(43.dp)
        // A draft of three lines says nothing about one line.
        fieldHeight.onMeasured(89.dp)
        assertThat(fieldHeight.value).isEqualTo(43.dp)
        // The device's font has taller lines than the estimate.
        fieldHeight.onMeasured(50.dp)
        assertThat(fieldHeight.value).isEqualTo(50.dp)
        fieldHeight.onMeasured(52.dp)
        assertThat(fieldHeight.value).isEqualTo(50.dp)
        fieldHeight.onMeasured(47.dp)
        assertThat(fieldHeight.value).isEqualTo(47.dp)
    }
}
