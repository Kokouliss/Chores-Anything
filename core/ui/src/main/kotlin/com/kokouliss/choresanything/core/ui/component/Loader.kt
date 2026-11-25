package com.kokouliss.choresanything.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.vectorResource
import com.kokouliss.choresanything.core.ui.R
import com.kokouliss.choresanything.core.ui.theme.Motion
import com.kokouliss.choresanything.core.ui.theme.rememberEndlessMillis
import com.kokouliss.choresanything.core.ui.theme.rememberReducedMotion

// One loop, in parts of 1: the pen draws the tick, it rests, then fades to come again.
private const val DRAW = 0.5f
private const val LEAVE_AT = 0.85f
private const val LEAVE = 0.1f

/** Longer than the tick, in the units of the logo. */
private const val TICK_LENGTH = 60f

/** A moment of the loop with the tick drawn: what a phone with animations off shows. */
internal const val LOADER_AT_REST = 0.7f

/** How much of the tick is drawn at [t] of the loop, 0 to 1. */
internal fun ticked(t: Float): Float = (t / DRAW).coerceIn(0f, 1f)

/** How far the tick has faded at the end of the loop. */
internal fun leaving(t: Float): Float = ((t - LEAVE_AT) / LEAVE).coerceIn(0f, 1f)

/**
 * The app logo on its tile while something loads. The house stands still; the tick on its door
 * draws itself, rests, and fades to be drawn again. Decoration: the caller says "Loading".
 */
@Composable
fun AppLoader(modifier: Modifier = Modifier) {
    val logo = ImageVector.vectorResource(R.drawable.core_ui_logo_chores)
    // The art of the logo itself, so nothing is drawn a second time: the house, then the tick.
    val group = logo.root.first() as VectorGroup
    val parts = remember(logo) { group.map { it as VectorPath }.map { PathParser().addPathNodes(it.pathData).toPath() to it } }
    val still = rememberReducedMotion()
    val millis = rememberEndlessMillis(moving = !still)
    Canvas(modifier.clip(LogoTileShape).background(LogoTile)) {
        val t = if (still) LOADER_AT_REST else millis.longValue % Motion.LOADER_LOOP / Motion.LOADER_LOOP.toFloat()
        scale(size.width / logo.viewportWidth, size.height / logo.viewportHeight, Offset.Zero) {
            parts.dropLast(1).forEach { (path, art) -> drawPath(path, checkNotNull(art.fill)) }
            val (tick, pen) = parts.last()
            val drawn = ticked(t)
            if (drawn == 0f) return@scale
            // A dash as long as the tick, slid along: the stroke shows as far as the pen has got.
            val unfinished = PathEffect.dashPathEffect(floatArrayOf(TICK_LENGTH, TICK_LENGTH), TICK_LENGTH * (1f - drawn))
            drawPath(
                tick,
                checkNotNull(pen.stroke),
                alpha = 1f - leaving(t),
                style = Stroke(pen.strokeLineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = unfinished.takeIf { drawn < 1f }),
            )
        }
    }
}
