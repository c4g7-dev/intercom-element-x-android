/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsDark
import io.element.android.compound.tokens.generated.compoundColorsLight

/**
 * InterCom's palette, as the desktop app has it (docs/DESIGN.md of the InterCom repository): near-black neutral
 * surfaces a step apart, separated by hairlines, and one accent that marks what is selected, unread or actionable.
 * Green stays what Compound makes of "success" (who speaks), red what it makes of "critical".
 *
 * These are Compound's semantic colours with the neutral and the accent roles replaced, so every component that
 * reads the theme follows, and whatever Compound adds later keeps its own value until it is given one here.
 */
object InterComPalette {
    // Dark. The greys have no blue in them, so pictures and shared screens keep their own colours next to them.
    val darkRail = Color(0xff0a0a0c)
    val darkSurface = Color(0xff101013)
    val darkRaised = Color(0xff151518)
    val darkHover = Color(0xff1a1a1e)
    val darkHairline = Color(0xff202025)
    val darkSelected = Color(0xff27272d)
    val darkBorder = Color(0xff3a3a41)
    val darkBorderStrong = Color(0xff6f6f79)
    val darkTextDisabled = Color(0xff73737d)
    val darkText = Color(0xfff0f0f3)
    val darkTextSecondary = Color(0xff8d8d97)
    val darkAccent = Color(0xff6875f5)

    /** The accent as text or as a thin icon on the dark surfaces: a step lighter, for a contrast of 6:1. */
    val darkAccentText = Color(0xff8089f7)
    val darkCritical = Color(0xffff5b55)

    // Light.
    val lightRail = Color(0xffeceef2)
    val lightSurface = Color(0xffffffff)
    val lightRaised = Color(0xfff6f7f9)
    val lightHover = Color(0xfff3f5f8)
    val lightHairline = Color(0xffe1e6ec)
    val lightSelected = Color(0xffe9ecf1)
    val lightBorder = Color(0xffc5cad3)
    val lightBorderStrong = Color(0xff656d77)
    val lightTextDisabled = Color(0xff7b828c)
    val lightText = Color(0xff1b1d22)
    val lightTextSecondary = Color(0xff656d77)
    val lightAccent = Color(0xff4f5bd5)
}

val interComColorsDark: SemanticColors = with(InterComPalette) {
    compoundColorsDark.copy(
        // Surfaces
        bgCanvasDefault = darkSurface,
        bgCanvasDefaultLevel1 = darkRaised,
        bgCanvasDisabled = darkRaised,
        bgSubtlePrimary = darkSelected,
        bgSubtleSecondary = darkHover,
        bgSubtleSecondaryLevel0 = darkSurface,
        bgSubtleTertiary = darkRail,
        bgBadgeDefault = darkSurface,
        bgBadgeSecondary = darkSelected,
        bgBadgePrimary = darkText,
        separatorPrimary = darkSelected,
        separatorSecondary = darkHairline,
        // Neutral actions
        bgActionPrimaryRest = darkText,
        bgActionPrimaryHovered = Color(0xffd6d6dc),
        bgActionPrimaryPressed = Color(0xffbcbcc4),
        bgActionPrimaryDisabled = Color(0xff4c4c54),
        bgActionSecondaryRest = darkSurface,
        bgActionSecondaryHovered = Color(0x14f0f0f3),
        bgActionSecondaryPressed = Color(0x24f0f0f3),
        bgActionTertiaryRest = darkSurface,
        bgActionTertiaryHovered = darkHover,
        bgActionTertiarySelected = darkSelected,
        borderDisabled = Color(0xff323237),
        borderInteractiveSecondary = darkBorder,
        borderInteractivePrimary = darkBorderStrong,
        borderInteractiveHovered = Color(0xffa5a5ae),
        // Text and icons
        textPrimary = darkText,
        textActionPrimary = darkText,
        textSecondary = darkTextSecondary,
        textDisabled = darkTextDisabled,
        textOnSolidPrimary = darkSurface,
        iconPrimary = darkText,
        iconPrimaryAlpha = darkText.copy(alpha = 0.96f),
        iconSecondary = darkTextSecondary,
        iconSecondaryAlpha = darkText.copy(alpha = 0.56f),
        iconTertiary = darkTextDisabled,
        iconTertiaryAlpha = darkText.copy(alpha = 0.42f),
        iconQuaternary = Color(0xff4c4c54),
        iconQuaternaryAlpha = darkText.copy(alpha = 0.28f),
        iconDisabled = Color(0xff4c4c54),
        iconOnSolidPrimary = darkSurface,
        // The one accent
        bgAccentRest = darkAccent,
        bgAccentHovered = Color(0xff7a86f7),
        bgAccentPressed = Color(0xff8c96f8),
        bgAccentSelected = darkAccent.copy(alpha = 0.22f),
        bgAccentSubtle = Color(0xff1a1c38),
        bgBadgeAccent = Color(0xff23264d),
        borderAccentPrimary = darkAccent,
        borderAccentSubtle = Color(0xff3f4699),
        borderFocused = darkAccentText,
        iconAccentPrimary = darkAccentText,
        iconAccentTertiary = darkAccent,
        textActionAccent = darkAccentText,
        textBadgeAccent = Color(0xffa3aafa),
        gradientActionStop1 = Color(0xff8c96f8),
        gradientActionStop2 = darkAccent,
        gradientActionStop3 = Color(0xff4d57c0),
        gradientActionStop4 = Color(0xff353c85),
        gradientSubtleStop1 = darkAccent.copy(alpha = 0.30f),
        gradientSubtleStop2 = darkAccent.copy(alpha = 0.22f),
        gradientSubtleStop3 = darkAccent.copy(alpha = 0.15f),
        gradientSubtleStop4 = darkAccent.copy(alpha = 0.09f),
        gradientSubtleStop5 = darkAccent.copy(alpha = 0.04f),
        gradientCriticalStop2 = darkSurface,
        gradientInfoStop2 = darkSurface,
        // Warning, muted, leave
        textCriticalPrimary = darkCritical,
        iconCriticalPrimary = darkCritical,
    )
}

val interComColorsLight: SemanticColors = with(InterComPalette) {
    compoundColorsLight.copy(
        bgCanvasDefault = lightSurface,
        bgCanvasDefaultLevel1 = lightSurface,
        bgCanvasDisabled = lightRaised,
        bgSubtlePrimary = lightSelected,
        bgSubtleSecondary = lightHover,
        bgSubtleSecondaryLevel0 = lightHover,
        bgSubtleTertiary = lightRaised,
        bgBadgeSecondary = lightSelected,
        bgBadgePrimary = lightText,
        separatorPrimary = lightHairline,
        separatorSecondary = lightSelected,
        bgActionPrimaryRest = lightText,
        bgActionPrimaryHovered = Color(0xff33363d),
        bgActionPrimaryPressed = Color(0xff474b54),
        bgActionTertiaryHovered = lightHover,
        bgActionTertiarySelected = lightSelected,
        borderInteractiveSecondary = lightBorder,
        borderInteractivePrimary = lightBorderStrong,
        textPrimary = lightText,
        textActionPrimary = lightText,
        textSecondary = lightTextSecondary,
        textDisabled = lightTextDisabled,
        iconPrimary = lightText,
        iconPrimaryAlpha = lightText.copy(alpha = 0.96f),
        iconSecondary = lightTextSecondary,
        iconSecondaryAlpha = lightText.copy(alpha = 0.66f),
        iconTertiary = lightTextDisabled,
        bgAccentRest = lightAccent,
        bgAccentHovered = Color(0xff434eba),
        bgAccentPressed = Color(0xff3942a0),
        bgAccentSelected = lightAccent.copy(alpha = 0.14f),
        bgAccentSubtle = Color(0xffeceefc),
        bgBadgeAccent = Color(0xffdfe2fa),
        borderAccentPrimary = lightAccent,
        borderAccentSubtle = Color(0xffa9b0ee),
        borderFocused = lightAccent,
        iconAccentPrimary = lightAccent,
        iconAccentTertiary = lightAccent,
        textActionAccent = lightAccent,
        textBadgeAccent = Color(0xff3942a0),
        gradientActionStop1 = Color(0xffa9b0ee),
        gradientActionStop2 = Color(0xff7c86e4),
        gradientActionStop3 = lightAccent,
        gradientActionStop4 = Color(0xff3942a0),
        gradientSubtleStop1 = lightAccent.copy(alpha = 0.22f),
        gradientSubtleStop2 = lightAccent.copy(alpha = 0.16f),
        gradientSubtleStop3 = lightAccent.copy(alpha = 0.11f),
        gradientSubtleStop4 = lightAccent.copy(alpha = 0.07f),
        gradientSubtleStop5 = lightAccent.copy(alpha = 0.03f),
    )
}

/**
 * Rounded, not soft: 6 dp for small controls, 8 dp for rows, buttons and menus, 12 dp for cards and sheets, 16 dp
 * for dialogs.
 */
val interComShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)
