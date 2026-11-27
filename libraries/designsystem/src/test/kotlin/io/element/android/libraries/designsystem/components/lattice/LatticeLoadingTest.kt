/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.lattice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LatticeLoadingTest {
    @Test
    fun `a bar is shortest at the ends of its period and full in the middle`() {
        assertThat(LatticeLoading.barScale(0f, delay = 0f)).isWithin(TOLERANCE).of(0.45f)
        assertThat(LatticeLoading.barScale(LatticeLoading.BAR_PERIOD / 2, delay = 0f)).isWithin(TOLERANCE).of(1f)
        assertThat(LatticeLoading.barScale(LatticeLoading.BAR_PERIOD, delay = 0f)).isWithin(TOLERANCE).of(0.45f)
    }

    @Test
    fun `a delay moves a bar's period, also one that lies before the start`() {
        assertThat(LatticeLoading.barScale(0.2f, delay = -0.35f)).isWithin(TOLERANCE).of(1f)
        assertThat(LatticeLoading.barScale(0.75f, delay = -0.35f)).isWithin(TOLERANCE).of(0.45f)
    }

    @Test
    fun `a dot is brightest two fifths into its period`() {
        assertThat(LatticeLoading.dotAlpha(0f, delay = 0f)).isWithin(TOLERANCE).of(0.18f)
        assertThat(LatticeLoading.dotAlpha(0.64f, delay = 0f)).isWithin(TOLERANCE).of(0.75f)
        assertThat(LatticeLoading.dotAlpha(0.64f + 0.12f, delay = 0.12f)).isWithin(TOLERANCE).of(0.75f)
    }

    @Test
    fun `every value stays in its range over the whole cycle, which ends where it began`() {
        var seconds = 0f
        while (seconds <= LatticeLoading.CYCLE_SECONDS) {
            LatticeLoading.bars.forEach { assertThat(LatticeLoading.barScale(seconds, it.delay)).isIn(com.google.common.collect.Range.closed(0.45f, 1.0001f)) }
            LatticeLoading.dots.forEach { assertThat(LatticeLoading.dotAlpha(seconds, it.delay)).isIn(com.google.common.collect.Range.closed(0.18f, 0.7501f)) }
            seconds += 0.05f
        }
        LatticeLoading.bars.forEach {
            assertThat(LatticeLoading.barScale(LatticeLoading.CYCLE_SECONDS, it.delay)).isWithin(0.01f).of(LatticeLoading.barScale(0f, it.delay))
        }
        LatticeLoading.dots.forEach {
            assertThat(LatticeLoading.dotAlpha(LatticeLoading.CYCLE_SECONDS, it.delay)).isWithin(0.01f).of(LatticeLoading.dotAlpha(0f, it.delay))
        }
    }

    @Test
    fun `standing still it is the icon`() {
        assertThat(LatticeLoading.barScale(null, delay = 0f)).isEqualTo(LatticeLoading.STILL_BAR_SCALE)
        assertThat(LatticeLoading.dotAlpha(null, delay = 0.36f)).isEqualTo(LatticeLoading.STILL_DOT_ALPHA)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
