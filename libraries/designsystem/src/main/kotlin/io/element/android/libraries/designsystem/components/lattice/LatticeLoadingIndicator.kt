/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.lattice

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlin.math.PI
import kotlin.math.cos

/**
 * Lattice: the app's loading animation, the three bars of its icon rising and falling between two rows of dots that
 * light up in turn (`lattice-loading.svg` of the icon set). With [showDots] off, the bars alone, for small sizes.
 * It stands still at its resting shape when Android's animations are turned off, and in previews.
 */
@Composable
fun LatticeLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    showDots: Boolean = true,
) {
    val seconds = rememberLatticeSeconds()
    val side = ElementTheme.colors.bgAccentRest
    val middle = ElementTheme.colors.textPrimary
    Canvas(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { },
    ) {
        // Read while drawing: a frame of the animation redraws, and composes nothing.
        drawLatticeLoading(seconds?.value, side = side, middle = middle, showDots = showDots)
    }
}

/**
 * Seconds since the animation began, wrapping where both of its periods do; null while it stands still: with
 * Android's animations turned off (Settings, Accessibility, Remove animations), and in previews and screenshots.
 */
@Composable
private fun rememberLatticeSeconds(): State<Float>? {
    val context = LocalContext.current
    val isStill = LocalInspectionMode.current || remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (isStill) return null
    val transition = rememberInfiniteTransition(label = "lattice")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = LatticeLoading.CYCLE_SECONDS,
        animationSpec = infiniteRepeatable(tween((LatticeLoading.CYCLE_SECONDS * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "latticeSeconds",
    )
}

private fun DrawScope.drawLatticeLoading(seconds: Float?, side: Color, middle: Color, showDots: Boolean) {
    // The icon's glyph, in the units of its 512 square: 244 wide, and 292 high with the dots, 160 without.
    val glyphHeight = if (showDots) 292f else 160f
    val unit = minOf(size.width / 244f, size.height / glyphHeight)
    val origin = Offset(size.width / 2 - 256f * unit, size.height / 2 - 256f * unit)
    fun at(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)
    if (showDots) {
        LatticeLoading.dots.forEach { dot ->
            drawCircle(side, radius = 22f * unit, center = at(dot.x, dot.y), alpha = LatticeLoading.dotAlpha(seconds, dot.delay))
        }
    }
    LatticeLoading.bars.forEachIndexed { index, bar ->
        val height = bar.height * LatticeLoading.barScale(seconds, bar.delay)
        drawRoundRect(
            color = if (index == 1) middle else side,
            topLeft = at(bar.x, 256f - height / 2),
            size = Size(44f * unit, height * unit),
            cornerRadius = CornerRadius(22f * unit),
        )
    }
}

/** The measures and the timing of the loading animation, as the icon set's SVG has them. */
internal object LatticeLoading {
    data class Bar(val x: Float, val height: Float, val delay: Float)

    data class Dot(val x: Float, val y: Float, val delay: Float)

    const val BAR_PERIOD = 1.1f
    const val DOT_PERIOD = 1.6f

    /** Where a bar period and a dot period end together. */
    const val CYCLE_SECONDS = 17.6f

    val bars = listOf(Bar(134f, 120f, -0.35f), Bar(234f, 160f, 0f), Bar(334f, 140f, -0.7f))

    /** Lit one after the other, round the bars: the top row from the left, then the bottom row from the right. */
    val dots = listOf(
        Dot(156f, 132f, 0f),
        Dot(256f, 132f, 0.12f),
        Dot(356f, 132f, 0.24f),
        Dot(356f, 380f, 0.36f),
        Dot(256f, 380f, 0.48f),
        Dot(156f, 380f, 0.6f),
    )

    private fun phase(seconds: Float, delay: Float, period: Float): Float = ((seconds - delay) % period + period) % period / period

    /** A bar's height as a share of its full one: 0.45 at the ends of its period, 1 in the middle. Still, the icon's own. */
    fun barScale(seconds: Float?, delay: Float): Float {
        if (seconds == null) return STILL_BAR_SCALE
        val phase = phase(seconds, delay, BAR_PERIOD)
        return 0.45f + 0.55f * (1f - cos(2 * PI.toFloat() * phase)) / 2f
    }

    /** A dot's opacity: 0.18, up to 0.75 at two fifths of its period, and back. Still, the icon's 0.4. */
    fun dotAlpha(seconds: Float?, delay: Float): Float {
        if (seconds == null) return STILL_DOT_ALPHA
        val phase = phase(seconds, delay, DOT_PERIOD)
        val rise = if (phase < 0.4f) phase / 0.4f else (1f - phase) / 0.6f
        return 0.18f + 0.57f * rise
    }

    const val STILL_BAR_SCALE = 0.85f
    const val STILL_DOT_ALPHA = 0.4f
}

@PreviewsDayNight
@Composable
internal fun LatticeLoadingIndicatorPreview() = ElementPreview {
    Row(modifier = Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LatticeLoadingIndicator()
        LatticeLoadingIndicator(size = 32.dp, showDots = false)
    }
}
