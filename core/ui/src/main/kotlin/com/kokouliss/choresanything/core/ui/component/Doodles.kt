package com.kokouliss.choresanything.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.kokouliss.choresanything.core.ui.theme.Motion
import com.kokouliss.choresanything.core.ui.theme.rememberReducedMotion

/**
 * A small pen drawing for a page with nothing on it yet. Drawn on a square of 96: [ink] is the pen,
 * [accent] the one stroke in the green of the app.
 */
enum class Doodle(internal val ink: String, internal val accent: String) {
    House(
        "M16 48L48 20L80 48M24 42V80H72V42M40 80V58H56V80",
        "M60 22V14H68V34",
    ),
    Broom(
        "M62 10L36 54M36 54L20 60L16 86H52L54 60L36 54ZM28 64L26 86M36 64V86M44 64L46 86",
        "M60 80C66 74 72 84 80 78",
    ),
    Cup(
        "M22 40H66V60C66 72 56 80 44 80C32 80 22 72 22 60ZM66 46H74C80 46 80 60 74 60H66M16 86H72",
        "M38 30C34 24 42 20 38 14M50 30C46 24 54 20 50 14",
    ),
    Pencil(
        "M62 16L78 32L36 74L16 80L22 60ZM54 24L70 40M22 60L36 74",
        "M46 86C54 78 58 92 66 84C71 80 75 88 82 84",
    ),
}

private const val DOODLE_SIZE = 96f

/** Longer than any one stroke of a doodle, in the units of its square. */
private const val LONGEST_STROKE = 400f

/** Decoration: the title under it says what the page is. */
@Composable
fun DoodleImage(doodle: Doodle, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val (inkPath, accentPath) = remember(doodle) {
        PathParser().parsePathString(doodle.ink).toPath() to PathParser().parsePathString(doodle.accent).toPath()
    }
    // The pen draws it once, when the page opens. With animations off on the phone it is there at once.
    val drawn = remember(doodle) { Animatable(0f) }
    val still = rememberReducedMotion()
    LaunchedEffect(doodle) { if (still) drawn.snapTo(1f) else drawn.animateTo(1f, tween(Motion.REVEAL)) }
    Canvas(modifier.size(DOODLE_SIZE.dp)) {
        scale(size.minDimension / DOODLE_SIZE, pivot = Offset.Zero) {
            // A dash as long as the longest stroke, slid along: every stroke shows as far as the pen has got.
            val unfinished = PathEffect.dashPathEffect(floatArrayOf(LONGEST_STROKE, LONGEST_STROKE), LONGEST_STROKE * (1f - drawn.value))
            val pen = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = unfinished.takeIf { drawn.value < 1f })
            drawPath(inkPath, ink, style = pen)
            drawPath(accentPath, accent, style = pen)
        }
    }
}
