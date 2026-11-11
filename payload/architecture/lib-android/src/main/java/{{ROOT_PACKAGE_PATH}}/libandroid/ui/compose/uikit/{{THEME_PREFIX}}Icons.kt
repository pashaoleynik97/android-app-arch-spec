// Path data reads as drawing commands, several per line (one stroke segment per line); splitting every
// statement onto its own line would make the icon shapes unreadable.
@file:Suppress("ktlint:standard:statement-wrapping")

package {{ROOT_PACKAGE}}.libandroid.ui.compose.uikit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The glyphs the base kit and the host chrome need, as project-local stroke [ImageVector]s (24×24 grid,
 * 2-unit round strokes). Defined in code so the kit carries no icon-library dependency; tint them at the call
 * site (`Icon(imageVector, tint = …)`).
 *
 * Add the design's icon set here (or as vector drawables) as screens need it.
 */
object {{THEME_PREFIX}}Icons {
    val ArrowBack: ImageVector = strokeIcon("ArrowBack") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f)
    }

    val Close: ImageVector = strokeIcon("Close") {
        moveTo(18f, 6f); lineTo(6f, 18f)
        moveTo(6f, 6f); lineTo(18f, 18f)
    }

    val Menu: ImageVector = strokeIcon("Menu") {
        moveTo(3f, 6f); lineTo(21f, 6f)
        moveTo(3f, 12f); lineTo(21f, 12f)
        moveTo(3f, 18f); lineTo(21f, 18f)
    }

    val Refresh: ImageVector = strokeIcon("Refresh") {
        moveTo(20f, 8f)
        curveTo(18.4f, 5.1f, 15.4f, 3.5f, 12f, 3.5f)
        curveTo(8.4f, 3.5f, 5.3f, 5.6f, 3.9f, 8.5f)
        moveTo(4f, 3.5f); lineTo(3.9f, 8.5f); lineTo(8.9f, 8.6f)
        moveTo(4f, 16f)
        curveTo(5.6f, 18.9f, 8.6f, 20.5f, 12f, 20.5f)
        curveTo(15.6f, 20.5f, 18.7f, 18.4f, 20.1f, 15.5f)
        moveTo(20f, 20.5f); lineTo(20.1f, 15.5f); lineTo(15.1f, 15.4f)
    }

    val AlertTriangle: ImageVector = strokeIcon("AlertTriangle") {
        moveTo(10.3f, 3.9f); lineTo(1.8f, 18f)
        curveTo(1.4f, 18.8f, 2f, 20f, 3f, 20f); lineTo(21f, 20f)
        curveTo(22f, 20f, 22.6f, 18.8f, 22.2f, 18f); lineTo(13.7f, 3.9f)
        curveTo(13f, 2.7f, 11f, 2.7f, 10.3f, 3.9f); close()
        moveTo(12f, 9f); lineTo(12f, 13f)
        moveTo(12f, 17f); lineTo(12.01f, 17f)
    }

    val CheckCircle: ImageVector = strokeIcon("CheckCircle") {
        circle(12f, 12f, 10f)
        moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
    }

    val Info: ImageVector = strokeIcon("Info") {
        circle(12f, 12f, 10f)
        moveTo(12f, 16f); lineTo(12f, 12f)
        moveTo(12f, 8f); lineTo(12.01f, 8f)
    }

    val ErrorCircle: ImageVector = strokeIcon("ErrorCircle") {
        circle(12f, 12f, 10f)
        moveTo(15f, 9f); lineTo(9f, 15f)
        moveTo(9f, 9f); lineTo(15f, 15f)
    }
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
    close()
}

private fun strokeIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}.build()
