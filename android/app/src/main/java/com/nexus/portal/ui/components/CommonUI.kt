package com.nexus.portal.ui.components

import dev.chrisbanes.haze.*

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.ui.theme.*
import com.nexus.portal.data.repository.ContentTheme
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import android.os.Build
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun TechGridBackground() {
    val colors = PortalTheme.colors
    val contentTheme = PortalTheme.contentTheme
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    var wallpaperUri by remember { mutableStateOf(prefs.getString("wallpaper_uri", null)) }
    
    DisposableEffect(context) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "wallpaper_uri") {
                wallpaperUri = sharedPreferences.getString("wallpaper_uri", null)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    if (wallpaperUri != null) {
        coil.compose.AsyncImage(
            model = wallpaperUri,
            contentDescription = "Background Wallpaper",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

        when (contentTheme) {
            ContentTheme.SNOOPY -> {
                // Playful yellow bubbles and golden round dots
                val step = 80.dp.toPx()
                var x = 0f
                while (x < width) {
                    var y = 0f
                    while (y < height) {
                        drawCircle(
                            color = colors.gridColor.copy(alpha = 0.15f),
                            radius = 3.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                        y += step
                    }
                    x += step
                }
                
                // Floating round Woodstock bubbles
                drawCircle(
                    color = colors.primary.copy(alpha = 0.04f),
                    radius = 40.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(width * 0.15f, height * 0.3f)
                )
                drawCircle(
                    color = colors.primary.copy(alpha = 0.04f),
                    radius = 60.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(width * 0.85f, height * 0.7f)
                )
            }
            ContentTheme.DARTH_VADER -> {
                // Tactical laser grid lines and high-alert borders
                val step = 50.dp.toPx()
                var x = step
                while (x < width - step) {
                    drawLine(
                        color = colors.border.copy(alpha = 0.12f),
                        start = androidx.compose.ui.geometry.Offset(x, step),
                        end = androidx.compose.ui.geometry.Offset(x, height - step),
                        strokeWidth = 2f
                    )
                    x += step
                }
                var y = step
                while (y < height - step) {
                    drawLine(
                        color = colors.border.copy(alpha = 0.12f),
                        start = androidx.compose.ui.geometry.Offset(step, y),
                        end = androidx.compose.ui.geometry.Offset(width - step, y),
                        strokeWidth = 2f
                    )
                    y += step
                }
            }
            ContentTheme.STANDARD -> {
                // Standard cyber grid
                val step = 60.dp.toPx()
                var x = step
                while (x < width - step) {
                    drawLine(
                        color = colors.gridColor,
                        start = androidx.compose.ui.geometry.Offset(x, step),
                        end = androidx.compose.ui.geometry.Offset(x, height - step),
                        strokeWidth = 1f
                    )
                    x += step
                }
                
                var y = step
                while (y < height - step) {
                    drawLine(
                        color = colors.gridColor,
                        start = androidx.compose.ui.geometry.Offset(step, y),
                        end = androidx.compose.ui.geometry.Offset(width - step, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
            }
        }
    }
    }
}

@Composable
fun GlassmorphicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
    tint: Color = Color.White.copy(alpha = 0.16f),
    content: @Composable BoxScope.() -> Unit
) {
    val colors = PortalTheme.colors
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val hazeState = com.nexus.portal.ui.theme.LocalHazeState.current
    val backdrop = com.nexus.portal.ui.theme.LocalBackgroundBackdrop.current

    val baseModifier = modifier
        .clip(shape)
        .drawBehind {
            val radius = cornerRadius.toPx()
            drawRoundRect(
                color = colors.border.copy(alpha = 0.4f),
                style = Stroke(width = 1f),
                cornerRadius = CornerRadius(radius, radius)
            )
        }

    val liquidConfig = com.nexus.portal.ui.liquid.LocalLiquidGlassConfig.current

    val glassModifier = if (liquidConfig.enabled && backdrop != null) {
        baseModifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(liquidConfig.blurRadius.dp.toPx())
                    lens(liquidConfig.lensRadiusX.dp.toPx(), liquidConfig.lensRadiusY.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(liquidConfig.tintColor.copy(alpha = 0.05f))
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
                }
            )
    } else if (isLiquidGlass && hazeState != null) {
        baseModifier
            .hazeChild(
                state = hazeState,
                shape = shape,
                style = HazeStyle(
                    blurRadius = 44.dp,
                    tint = tint.copy(alpha = 0.22f),
                    noiseFactor = 0.06f
                )
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.04f),
                        colors.surfaceCard.copy(alpha = 0.35f)
                    )
                )
            )
    } else {
        baseModifier.background(colors.surfaceCard.copy(alpha = 0.82f))
    }

    Box(modifier = glassModifier, content = content)
}

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassmorphicSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tint = PortalTheme.colors.primary.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
fun ScrollContainerContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Eager liquid-glass container implementation
    GlassmorphicCard(
        modifier = modifier,
        content = content
    )
}


@Composable
fun GlassmorphicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowingColor: Color = Color(0xFF00FFCC),
    icon: @Composable (() -> Unit)? = null
) {
    val backdrop = com.nexus.portal.ui.theme.LocalBackgroundBackdrop.current
    val config = com.nexus.portal.ui.liquid.LocalLiquidGlassConfig.current
    
    if (config.enabled && backdrop != null) {
        com.kyant.backdrop.catalog.components.LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier.fillMaxWidth(),
            tint = Color.Unspecified,
            surfaceColor = glowingColor.copy(alpha = 0.05f)
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = text,
                color = PortalTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }
    } else {
        // Fallback for non-liquid glass
        val colors = PortalTheme.colors
        val baseModifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(glowingColor.copy(alpha = 0.3f), glowingColor.copy(alpha = 0.1f))
                )
            )

        Box(
            modifier = baseModifier
                .border(1.dp, glowingColor.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = text,
                    color = PortalTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun NeonProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00FFCC)
) {
    val infiniteTransition = rememberInfiniteTransition()
    val animatedPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Downloading Update", color = TextSecondary, fontSize = 13.sp)
            Text(text = "${(progress * 100).toInt()}%", color = color, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF161622))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = animatedPulse))
            )
        }
    }
}

@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedColor: Color = PortalTheme.colors.primary,
    uncheckedColor: Color = PortalTheme.colors.surfaceCard
) {
    val backdrop = com.nexus.portal.ui.theme.LocalBackgroundBackdrop.current
    val config = com.nexus.portal.ui.liquid.LocalLiquidGlassConfig.current

    if (config.enabled && backdrop != null) {
        com.kyant.backdrop.catalog.components.LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = backdrop,
            modifier = modifier
        )
    } else {
        // Fallback
        val thumbOffset by animateFloatAsState(targetValue = if (checked) 24f else 4f, label = "thumbOffset")
        val trackColor = if (checked) checkedColor.copy(alpha = 0.4f) else uncheckedColor.copy(alpha = 0.4f)
        
        Box(
            modifier = modifier
                .width(52.dp)
                .height(32.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(trackColor)
                .clickable(
                    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onCheckedChange(!checked) }
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(thumbOffset.dp.roundToPx(), 0) }
                    .align(Alignment.CenterStart)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun GlassmorphicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier
) {
    val backdrop = com.nexus.portal.ui.theme.LocalBackgroundBackdrop.current
    val config = com.nexus.portal.ui.liquid.LocalLiquidGlassConfig.current

    if (config.enabled && backdrop != null) {
        com.kyant.backdrop.catalog.components.LiquidSlider(
            value = { value },
            onValueChange = onValueChange,
            valueRange = valueRange,
            visibilityThreshold = 0.01f,
            backdrop = backdrop,
            modifier = modifier
        )
    } else {
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = modifier
        )
    }
}
