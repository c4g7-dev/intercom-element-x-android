/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsDark
import io.element.android.compound.tokens.generated.compoundColorsLight
import org.junit.Test

class InterComColorsTest {
    /** The WCAG contrast ratio of [foreground] on [background]. */
    private fun contrast(foreground: Color, background: Color): Float {
        val a = foreground.compositeOver(background).luminance()
        val b = background.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }

    private fun SemanticColors.surfaces() = listOf(bgCanvasDefault, bgCanvasDefaultLevel1, bgSubtleSecondary, bgSubtlePrimary, bgSubtleTertiary)

    @Test
    fun `InterCom's palette is what the app is drawn with`() {
        assertThat(SemanticColorsLightDark.default.dark).isEqualTo(interComColorsDark)
        assertThat(SemanticColorsLightDark.default.light).isEqualTo(interComColorsLight)
        assertThat(interComColorsDark.isLight).isFalse()
        assertThat(interComColorsLight.isLight).isTrue()
    }

    @Test
    fun `the dark theme has the desktop's surfaces, hairline and accent`() {
        with(interComColorsDark) {
            assertThat(bgSubtleTertiary).isEqualTo(Color(0xff0a0a0c))
            assertThat(bgCanvasDefault).isEqualTo(Color(0xff101013))
            assertThat(bgCanvasDefaultLevel1).isEqualTo(Color(0xff151518))
            assertThat(separatorSecondary).isEqualTo(Color(0xff202025))
            assertThat(bgAccentRest).isEqualTo(Color(0xff6875f5))
            assertThat(textPrimary).isEqualTo(Color(0xfff0f0f3))
            assertThat(textSecondary).isEqualTo(Color(0xff8d8d97))
        }
        assertThat(interComColorsLight.bgAccentRest).isEqualTo(Color(0xff4f5bd5))
        assertThat(interComColorsLight.textPrimary).isEqualTo(Color(0xff1b1d22))
    }

    @Test
    fun `green stays for success and nothing else, the accent replaces it everywhere else`() {
        for ((ours, compound) in listOf(interComColorsDark to compoundColorsDark, interComColorsLight to compoundColorsLight)) {
            assertThat(ours.textSuccessPrimary).isEqualTo(compound.textSuccessPrimary)
            assertThat(ours.iconSuccessPrimary).isEqualTo(compound.iconSuccessPrimary)
            assertThat(ours.bgSuccessRest).isEqualTo(compound.bgSuccessRest)
            val green = compound.bgAccentRest
            val accentRoles = listOf(
                ours.bgAccentRest,
                ours.bgAccentHovered,
                ours.bgAccentPressed,
                ours.borderAccentPrimary,
                ours.borderAccentSubtle,
                ours.iconAccentPrimary,
                ours.iconAccentTertiary,
                ours.textActionAccent,
                ours.textBadgeAccent,
                ours.bgBadgeAccent,
                ours.bgAccentSubtle,
                ours.gradientActionStop1,
                ours.gradientActionStop2,
                ours.gradientActionStop3,
                ours.gradientActionStop4,
            )
            assertThat(accentRoles).doesNotContain(green)
            // Blue more than green, in every accent role.
            accentRoles.forEach { assertThat(it.blue).isGreaterThan(it.green) }
        }
    }

    @Test
    fun `text is readable on every surface`() {
        for (colors in listOf(interComColorsDark, interComColorsLight)) {
            // What text is written on: the canvas, what is raised from it, and a row that is pressed.
            for (surface in listOf(colors.bgCanvasDefault, colors.bgCanvasDefaultLevel1, colors.bgSubtleSecondary)) {
                assertThat(contrast(colors.textSecondary, surface)).isAtLeast(4.5f)
                assertThat(contrast(colors.textActionAccent, surface)).isAtLeast(4.5f)
                assertThat(contrast(colors.textCriticalPrimary, surface)).isAtLeast(4.5f)
            }
            for (surface in colors.surfaces()) {
                assertThat(contrast(colors.textPrimary, surface)).isAtLeast(7f)
                assertThat(contrast(colors.textSecondary, surface)).isAtLeast(4f)
                assertThat(contrast(colors.textActionAccent, surface)).isAtLeast(4f)
                assertThat(contrast(colors.textSuccessPrimary, surface)).isAtLeast(3f)
                assertThat(contrast(colors.iconSecondary, surface)).isAtLeast(3f)
                assertThat(contrast(colors.iconTertiary, surface)).isAtLeast(3f)
                assertThat(contrast(colors.textDisabled, surface)).isAtLeast(3f)
            }
            // What is written on a filled button.
            assertThat(contrast(colors.textOnSolidPrimary, colors.bgAccentRest)).isAtLeast(4.5f)
            assertThat(contrast(colors.textOnSolidPrimary, colors.bgActionPrimaryRest)).isAtLeast(7f)
            assertThat(contrast(colors.textOnSolidPrimary, colors.bgCriticalPrimary)).isAtLeast(4.5f)
            assertThat(contrast(colors.textBadgeAccent, colors.bgBadgeAccent)).isAtLeast(4.5f)
            assertThat(contrast(colors.textActionAccent, colors.bgAccentSubtle)).isAtLeast(4.5f)
        }
    }

    @Test
    fun `the surfaces are a step apart and the greys of the dark theme are neutral`() {
        with(interComColorsDark) {
            assertThat(bgSubtleTertiary.luminance()).isLessThan(bgCanvasDefault.luminance())
            assertThat(bgCanvasDefault.luminance()).isLessThan(bgCanvasDefaultLevel1.luminance())
            assertThat(bgCanvasDefaultLevel1.luminance()).isLessThan(bgSubtleSecondary.luminance())
            assertThat(bgSubtleSecondary.luminance()).isLessThan(bgSubtlePrimary.luminance())
            for (grey in surfaces() + listOf(separatorPrimary, separatorSecondary)) {
                assertThat(grey.blue - grey.red).isLessThan(0.03f)
            }
        }
    }
}
