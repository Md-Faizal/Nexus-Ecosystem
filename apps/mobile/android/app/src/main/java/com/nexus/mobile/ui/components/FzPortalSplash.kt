package com.nexus.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val WING_DOWN_SVG =
    "M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z"
private const val WING_UP_SVG =
    "M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z"

private val Z_HOLE_POINTS = listOf(
    64.74f to 20.95f,
    128.88f to 20.22f,
    121.68f to 37.77f,
    78.77f to 57.7f,
    113.0f to 58.08f,
    108.01f to 69.85f,
    44.55f to 69.8f,
    51.04f to 53.07f,
    93.98f to 33.19f,
    59.67f to 32.61f,
)

/**
 * X-style opening zoom animation combined with the portfolio FZ Monogram Z-portal reveal:
 * - Always pitch-black (#000000) background regardless of app Light/Dark mode.
 * - Original lime green (#55FF00) FZ wings.
 * - Center Z starts solidly filled (#050507) so nothing peeks through, then opens as the portal zooms.
 */
@Composable
fun FzPortalSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canvasColor = Color.Black
    val limeWingColor = Color(0xFF55FF00)
    val zFillColor = Color(0xFF050507)
    val zStrokeColor = Color.White

    val introProgress = remember { Animatable(0f) }
    val zoomScale = remember { Animatable(1f) }
    var isDone by remember { mutableStateOf(false) }

    val wingDownPath = remember { PathParser().parsePathString(WING_DOWN_SVG).toPath() }
    val wingUpPath = remember { PathParser().parsePathString(WING_UP_SVG).toPath() }
    val zBasePath = remember {
        Path().apply {
            Z_HOLE_POINTS.forEachIndexed { idx, (x, y) ->
                if (idx == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    LaunchedEffect(Unit) {
        introProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
        delay(140)
        // Twitter / X-style dramatic anticipation pull-back before punching forward
        zoomScale.animateTo(
            targetValue = 0.84f,
            animationSpec = tween(
                durationMillis = 130,
                easing = FastOutSlowInEasing,
            ),
        )
        zoomScale.animateTo(
            targetValue = 12.5f,
            animationSpec = tween(
                durationMillis = 680,
                easing = CubicBezierEasing(0.75f, 0f, 0.25f, 1f),
            ),
        )
        isDone = true
        onFinished()
    }

    if (isDone) return

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleFactor = zoomScale.value
            val isPortalOpen = scaleFactor > 1.04f

            val overlayAlpha = if (scaleFactor >= 5.5f) {
                (1f - (scaleFactor - 5.5f) / 4.0f).coerceIn(0f, 1f)
            } else {
                1f
            }
            val emblemAlpha = if (scaleFactor >= 2.5f) {
                (1f - (scaleFactor - 2.5f) / 2.8f).coerceIn(0f, 1f)
            } else {
                introProgress.value
            }

            val logoBaseWidth = (size.width * 0.44f).coerceIn(140.dp.toPx(), 240.dp.toPx())
            val baseScale = (logoBaseWidth / 171.63f) * (0.88f + 0.12f * introProgress.value)
            val totalScale = baseScale * scaleFactor

            val cx = 85.815f
            val cy = 45.5f
            val screenCx = size.width / 2f
            val screenCy = size.height / 2f

            if (!isPortalOpen) {
                // Stage 1: Solid pitch-black canvas (nothing peeks through before zoom begins)
                drawRect(color = canvasColor)
            } else {
                // Stage 2: Punch out the expanding Z portal hole smoothly
                val cutoutPath = Path().apply {
                    Z_HOLE_POINTS.forEachIndexed { idx, (x, y) ->
                        val px = screenCx + (x - cx) * 1.018f * totalScale
                        val py = screenCy + (y - cy) * 1.018f * totalScale
                        if (idx == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                clipPath(path = cutoutPath, clipOp = ClipOp.Difference) {
                    drawRect(color = canvasColor.copy(alpha = overlayAlpha))
                }
            }

            // Crisp lime FZ monogram wings + filled Z core during intro
            if (emblemAlpha > 0.01f) {
                withTransform({
                    translate(left = screenCx - cx * totalScale, top = screenCy - cy * totalScale)
                    scale(scaleX = totalScale, scaleY = totalScale, pivot = Offset.Zero)
                }) {
                    drawPath(wingDownPath, color = limeWingColor.copy(alpha = emblemAlpha))
                    drawPath(wingUpPath, color = limeWingColor.copy(alpha = emblemAlpha))
                    if (!isPortalOpen) {
                        drawPath(zBasePath, color = zFillColor.copy(alpha = emblemAlpha))
                        drawPath(
                            zBasePath,
                            color = zStrokeColor.copy(alpha = emblemAlpha * 0.85f),
                            style = Stroke(width = 1.4f),
                        )
                    }
                }
            }
        }
    }
}
