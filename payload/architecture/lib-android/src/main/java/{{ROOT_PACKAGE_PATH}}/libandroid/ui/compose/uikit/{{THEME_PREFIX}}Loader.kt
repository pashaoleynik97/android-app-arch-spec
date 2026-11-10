package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import {{ROOT_PACKAGE}}.libandroid.ui.preview.ReferenceWidthPreview
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The design system's loading indicator: [rayCount] rounded spokes, a bright head stepping one ray per
 * [frameMillis] with brightness trailing behind it. [color] is the bright ray colour (dim rays are the same hue
 * at reduced alpha), so the caller controls contrast against its background.
 *
 * Swap the drawing for the design's own loader when it arrives; keep the signature.
 */
@Composable
fun {{THEME_PREFIX}}Loader(
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    rayCount: Int = 8,
    frameMillis: Long = 90L,
) {
    var head by remember { mutableIntStateOf(0) }
    LaunchedEffect(rayCount, frameMillis) {
        while (true) {
            delay(frameMillis)
            head = (head + 1) % rayCount
        }
    }

    Canvas(modifier = modifier.size(diameter)) {
        val outer = size.minDimension / 2f
        val inner = outer * INNER_FRACTION
        val stroke = outer * STROKE_FRACTION
        for (i in 0 until rayCount) {
            // How many steps ray i sits behind the head: 0 is the head (brightest), fading around the circle.
            val behind = ((head - i) % rayCount + rayCount) % rayCount
            val brightness = 1f - behind.toFloat() / rayCount
            val alpha = DIM_ALPHA + (1f - DIM_ALPHA) * brightness
            val angle = (i.toFloat() / rayCount) * TWO_PI - HALF_PI
            val dx = cos(angle)
            val dy = sin(angle)
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(center.x + dx * inner, center.y + dy * inner),
                end = Offset(center.x + dx * outer, center.y + dy * outer),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * A full-size blocking overlay: a translucent scrim with a centred loader that swallows input. The top layer
 * of a screen while a request that must not be interrupted runs.
 */
@Composable
fun {{THEME_PREFIX}}LoadingFrame(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background({{THEME_PREFIX}}Theme.colors.background.copy(alpha = SCRIM_ALPHA))
            .pointerInput(Unit) { detectTapGestures { /* swallow taps — block the content underneath */ } },
        contentAlignment = Alignment.Center,
    ) {
        {{THEME_PREFIX}}Loader(color = {{THEME_PREFIX}}Theme.colors.primary, diameter = 56.dp)
    }
}

private const val INNER_FRACTION = 0.5f
private const val STROKE_FRACTION = 0.16f
private const val DIM_ALPHA = 0.22f
private const val SCRIM_ALPHA = 0.7f
private val TWO_PI = (2.0 * PI).toFloat()
private val HALF_PI = (PI / 2.0).toFloat()

@ReferenceWidthPreview
@Composable
private fun LoaderPreview() = {{THEME_PREFIX}}Theme {
    {{THEME_PREFIX}}Loader(color = {{THEME_PREFIX}}Theme.colors.primary)
}
