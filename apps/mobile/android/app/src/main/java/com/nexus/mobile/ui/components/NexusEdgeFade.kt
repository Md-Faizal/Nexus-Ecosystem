package com.nexus.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class EdgeFadePlacement { TOP, BOTTOM, BOTH, NONE }

@Immutable
sealed interface EdgeFadeColor {
    /** Automatically follows MaterialTheme.colorScheme.background (Pitch Black / Pure White). */
    data object AutoTheme : EdgeFadeColor
    /** Fixed custom color (e.g. page accent or album/hero dominant color). */
    data class Fixed(val color: Color) : EdgeFadeColor
    /** No color scrim rendered. */
    data object None : EdgeFadeColor
}

@Immutable
sealed interface EdgeFadeReaction {
    /** Always visible at full strength. */
    data object AlwaysVisible : EdgeFadeReaction
    /** Hidden at scroll top, smoothly fades in once scrolled past [triggerItemIndex]/[triggerOffsetPx]. */
    data class ScrollLinked(
        val triggerItemIndex: Int = 0,
        val triggerOffsetPx: Int = 40,
    ) : EdgeFadeReaction
    /** Fixed custom opacity [0f..1f]. */
    data class Static(val alpha: Float) : EdgeFadeReaction
}

@Immutable
data class NexusEdgeFadeConfig(
    val placement: EdgeFadePlacement = EdgeFadePlacement.BOTH,
    val topColor: EdgeFadeColor = EdgeFadeColor.AutoTheme,
    val bottomColor: EdgeFadeColor = EdgeFadeColor.AutoTheme,
    val topReaction: EdgeFadeReaction = EdgeFadeReaction.ScrollLinked(),
    val bottomReaction: EdgeFadeReaction = EdgeFadeReaction.AlwaysVisible,
    val topExtraHeight: Dp = 46.dp,
    val bottomExtraHeight: Dp = 44.dp,
    val topMaxAlpha: Float = 0.94f,
    val bottomMaxAlpha: Float = 0.94f,
)

val LocalNexusEdgeFadeConfig = staticCompositionLocalOf { NexusEdgeFadeConfig() }

@Composable
fun EdgeFadeColor.resolveColor(): Color? = when (this) {
    EdgeFadeColor.AutoTheme -> MaterialTheme.colorScheme.background
    is EdgeFadeColor.Fixed -> color
    EdgeFadeColor.None -> null
}

@Composable
fun rememberEdgeFadeAlpha(
    reaction: EdgeFadeReaction,
    listState: LazyListState? = null,
): Float {
    return when (reaction) {
        EdgeFadeReaction.AlwaysVisible -> 1f
        is EdgeFadeReaction.Static -> reaction.alpha.coerceIn(0f, 1f)
        is EdgeFadeReaction.ScrollLinked -> {
            if (listState == null) {
                0f
            } else {
                val triggered by remember(listState, reaction) {
                    derivedStateOf {
                        listState.firstVisibleItemIndex >= reaction.triggerItemIndex ||
                            listState.firstVisibleItemScrollOffset > reaction.triggerOffsetPx
                    }
                }
                val animated by animateFloatAsState(
                    targetValue = if (triggered) 1f else 0f,
                    animationSpec = tween(durationMillis = 240),
                    label = "EdgeFadeAlpha",
                )
                animated
            }
        }
    }
}

fun Modifier.nexusEdgeFadeBrush(
    color: Color,
    topToBottom: Boolean,
    maxAlpha: Float = 0.88f,
): Modifier = this.background(
    brush = Brush.verticalGradient(
        colors = if (topToBottom) {
            listOf(
                color.copy(alpha = maxAlpha),
                color.copy(alpha = maxAlpha * 0.58f),
                Color.Transparent,
            )
        } else {
            listOf(
                Color.Transparent,
                color.copy(alpha = maxAlpha * 0.58f),
                color.copy(alpha = maxAlpha),
            )
        },
    ),
)

/**
 * Global drop-in edge gradient fade wrapper for any screen.
 * Configurable via [config] (defaults to [LocalNexusEdgeFadeConfig.current]).
 */
@Composable
fun NexusEdgeFadeContainer(
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    config: NexusEdgeFadeConfig = LocalNexusEdgeFadeConfig.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val showTop = config.placement == EdgeFadePlacement.TOP || config.placement == EdgeFadePlacement.BOTH
    val showBottom = config.placement == EdgeFadePlacement.BOTTOM || config.placement == EdgeFadePlacement.BOTH

    val resolvedTopColor = if (showTop) config.topColor.resolveColor() else null
    val resolvedBottomColor = if (showBottom) config.bottomColor.resolveColor() else null

    val topAlpha = if (resolvedTopColor != null) rememberEdgeFadeAlpha(config.topReaction, listState) else 0f
    val bottomAlpha = if (resolvedBottomColor != null) rememberEdgeFadeAlpha(config.bottomReaction, listState) else 0f

    Box(modifier = modifier.fillMaxSize()) {
        content()

        if (resolvedTopColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarTop + config.topExtraHeight)
                    .graphicsLayer { alpha = topAlpha }
                    .nexusEdgeFadeBrush(
                        color = resolvedTopColor,
                        topToBottom = true,
                        maxAlpha = config.topMaxAlpha,
                    ),
            )
        }

        if (resolvedBottomColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navBarBottom + config.bottomExtraHeight)
                    .graphicsLayer { alpha = bottomAlpha }
                    .nexusEdgeFadeBrush(
                        color = resolvedBottomColor,
                        topToBottom = false,
                        maxAlpha = config.bottomMaxAlpha,
                    ),
            )
        }
    }
}
