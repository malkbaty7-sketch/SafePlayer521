package com.example.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * CompositionLocal providing the active [LiquidGlassController] across the player overlay tree.
 */
val LocalLiquidGlassController = compositionLocalOf { LiquidGlassController() }

/**
 * Core reusable Frosted / Liquid Glass container that sits directly on top of an active
 * ExoPlayer / Media3 video surface.
 *
 * Technical Architecture:
 * 1. **Backdrop Layer (Offscreen Hardware RenderNode)**:
 *    Draws the spatially-aligned live video frame behind this container's exact root coordinates,
 *    applies hardware-accelerated `RenderEffect.createBlurEffect` (API 31+) only to the background
 *    layer so foreground icons/text remain razor-sharp, and blends [tintColor] at [glassOpacity].
 * 2. **Liquid Specular Refraction**:
 *    Adds a multi-stop diagonal glass sheen plus a 1.dp gradient border stroke combining
 *    semi-transparent white and warm champagne highlights to simulate curved liquid glass edges.
 * 3. **Crisp Foreground Slot**:
 *    Hosts interactive player controls, sliders, menus, or sheet content without blur distortion.
 */
@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    controller: LiquidGlassController = LocalLiquidGlassController.current,
    shape: Shape = RoundedCornerShape(22.dp),
    blurRadiusOverride: Float? = null,
    glassOpacityOverride: Float? = null,
    tintColorOverride: Color? = null,
    borderWidth: Dp = 1.dp,
    isHighlighted: Boolean = false,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val config by controller.config.collectAsState()
    val backdropFrame by controller.backdropFrame.collectAsState()
    val surfaceSize by controller.surfaceSize.collectAsState()
    val density = LocalDensity.current

    val effectiveBlurDp = (blurRadiusOverride ?: config.blurRadius).coerceIn(
        LiquidGlassController.MIN_BLUR_RADIUS,
        LiquidGlassController.MAX_BLUR_RADIUS
    )
    val effectiveOpacity = (glassOpacityOverride ?: config.glassOpacity).coerceIn(
        LiquidGlassController.MIN_OPACITY,
        LiquidGlassController.MAX_OPACITY
    )
    val effectiveTint = tintColorOverride ?: config.tintColor

    val animatedBlurDp by animateFloatAsState(
        targetValue = effectiveBlurDp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glass_blur_radius"
    )
    val animatedOpacity by animateFloatAsState(
        targetValue = effectiveOpacity,
        animationSpec = tween(180),
        label = "glass_surface_opacity"
    )
    val animatedTint by animateColorAsState(
        targetValue = if (isHighlighted) {
            Color(
                red = (effectiveTint.red * 0.55f + highlightColor.red * 0.45f).coerceIn(0f, 1f),
                green = (effectiveTint.green * 0.55f + highlightColor.green * 0.45f).coerceIn(0f, 1f),
                blue = (effectiveTint.blue * 0.55f + highlightColor.blue * 0.45f).coerceIn(0f, 1f),
                alpha = 1f
            )
        } else {
            effectiveTint
        },
        animationSpec = tween(200),
        label = "glass_tint_color"
    )

    var containerOffsetInRoot by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Liquid glass 1dp gradient border brush (semi-transparent white + warm champagne highlight)
    val specularAlpha = config.specularHighlightAlpha
    val warmAlpha = config.warmEdgeHighlightAlpha
    val borderBrush = remember(specularAlpha, warmAlpha, isHighlighted, highlightColor) {
        if (isHighlighted) {
            Brush.linearGradient(
                colors = listOf(
                    highlightColor.copy(alpha = 0.85f),
                    Color(0xFFFFE0B2).copy(alpha = (warmAlpha + 0.2f).coerceIn(0f, 0.9f)),
                    highlightColor.copy(alpha = 0.45f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = specularAlpha.coerceIn(0.08f, 0.85f)),
                    Color(0xFFFFE4B5).copy(alpha = warmAlpha.coerceIn(0.05f, 0.75f)),
                    Color.White.copy(alpha = (specularAlpha * 0.22f).coerceIn(0.04f, 0.35f)),
                    Color(0xFFFFCC80).copy(alpha = (warmAlpha * 0.45f).coerceIn(0.04f, 0.45f))
                )
            )
        }
    }

    val blurPx = with(density) { animatedBlurDp.dp.toPx() }

    val baseModifier = modifier
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .onGloballyPositioned { coords ->
            containerOffsetInRoot = coords.positionInRoot()
            containerSize = coords.size
        }
        .clip(shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Box(modifier = baseModifier) {
        // LAYER 1: Hardware-Accelerated Frosted Glass Backdrop (API 31+ RenderEffect.createBlurEffect)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0.5f) {
                        renderEffect = RenderEffect.createBlurEffect(
                            blurPx,
                            blurPx,
                            Shader.TileMode.MIRROR
                        ).asComposeRenderEffect()
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val frame = backdropFrame
                val rootW = surfaceSize.width.takeIf { it > 0 } ?: size.width.toInt()
                val rootH = surfaceSize.height.takeIf { it > 0 } ?: size.height.toInt()

                if (frame != null && config.enableLiveVideoRefraction && rootW > 0 && rootH > 0) {
                    // Draw the full live video frame offset by -containerOffsetInRoot so the region
                    // inside this container aligns spatially with the video directly behind it.
                    drawImage(
                        image = frame,
                        dstOffset = IntOffset(
                            x = -containerOffsetInRoot.x.roundToInt(),
                            y = -containerOffsetInRoot.y.roundToInt()
                        ),
                        dstSize = IntSize(width = rootW, height = rootH),
                        alpha = 0.96f,
                        filterQuality = FilterQuality.Medium
                    )
                } else {
                    // Fallback atmospheric depth layer when no video frame is active or on API < 31
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedTint.copy(alpha = (animatedOpacity * 0.85f).coerceIn(0.15f, 0.92f)),
                                Color(0xFF060911).copy(alpha = (animatedOpacity + 0.18f).coerceIn(0.25f, 0.95f))
                            ),
                            center = Offset(size.width * 0.3f, size.height * 0.2f),
                            radius = size.maxDimension.coerceAtLeast(100f)
                        )
                    )
                }

                // Blend the user-selected tintColor with glassOpacity (surfaceAlpha)
                drawRect(
                    color = animatedTint.copy(alpha = animatedOpacity)
                )

                // On API < 31 where hardware RenderEffect blur is unavailable, add extra frosted diffusion
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && animatedBlurDp > 1f) {
                    val fallbackFrostAlpha = ((animatedBlurDp / 35f) * 0.38f).coerceIn(0.08f, 0.38f)
                    drawRect(
                        color = Color(0xFF121824).copy(alpha = fallbackFrostAlpha)
                    )
                }
            }
        }

        // LAYER 2: Liquid Glass Specular Sheen & Inner Rim Refraction (Unblurred for crisp glass feel)
        Canvas(modifier = Modifier.matchParentSize()) {
            // Diagonal liquid surface reflection
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (specularAlpha * 0.24f).coerceIn(0.02f, 0.22f)),
                        Color(0xFFFFE0B2).copy(alpha = (warmAlpha * 0.14f).coerceIn(0.01f, 0.14f)),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.14f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            // Subtle top specular highlight bar simulating overhead light on curved glass
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = (specularAlpha * 0.55f).coerceIn(0.05f, 0.55f)),
                        Color(0xFFFFE0B2).copy(alpha = (warmAlpha * 0.45f).coerceIn(0.04f, 0.45f)),
                        Color.Transparent
                    )
                ),
                start = Offset(size.width * 0.08f, 1.dp.toPx()),
                end = Offset(size.width * 0.92f, 1.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        // LAYER 3: 1.dp Liquid Gradient Border Stroke + Crisp Foreground Content
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(BorderStroke(borderWidth, borderBrush), shape)
        )

        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Reusable Glassmorphic Pill Button for video player quick actions and status chips.
 */
@Composable
fun LiquidGlassPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    controller: LiquidGlassController = LocalLiquidGlassController.current,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    testTag: String? = null
) {
    LiquidGlassContainer(
        controller = controller,
        shape = RoundedCornerShape(50),
        isHighlighted = isActive,
        highlightColor = activeColor,
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
        onClick = onClick,
        testTag = testTag,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.White else Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Reusable Glassmorphic Icon Button for top bar and transport controls.
 */
@Composable
fun LiquidGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 19.dp,
    isActive: Boolean = false,
    iconTint: Color = Color.White,
    controller: LiquidGlassController = LocalLiquidGlassController.current,
    testTag: String? = null
) {
    LiquidGlassContainer(
        controller = controller,
        shape = CircleShape,
        isHighlighted = isActive,
        onClick = onClick,
        testTag = testTag,
        modifier = modifier.size(size)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) MaterialTheme.colorScheme.primary else iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Data model for items in a [LiquidGlassPopupMenu].
 */
data class LiquidGlassMenuItem(
    val title: String,
    val icon: ImageVector,
    val iconTint: Color? = null,
    val badgeText: String? = null,
    val isSwitch: Boolean = false,
    val switchChecked: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Floating Glassmorphic Pop-up Menu that sits directly on top of the active video surface
 * so the live blurred video frame is visible right through the menu.
 */
@Composable
fun LiquidGlassPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<LiquidGlassMenuItem>,
    modifier: Modifier = Modifier,
    controller: LiquidGlassController = LocalLiquidGlassController.current
) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.92f, animationSpec = tween(150)),
        modifier = modifier
    ) {
        LiquidGlassContainer(
            controller = controller,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.widthIn(min = 230.dp, max = 280.dp),
            testTag = "liquid_glass_popup_menu"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.onClick() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = item.iconTint ?: Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.badgeText != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = item.badgeText,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (item.isSwitch) {
                            Switch(
                                checked = item.switchChecked,
                                onCheckedChange = { item.onClick() },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Glassmorphic Bottom Sheet overlay that renders directly inside the Player hierarchy
 * over the active ExoPlayer / Media3 surface, allowing live video frames to blur in real time
 * behind the sheet instead of creating an opaque system window.
 */
@Composable
fun LiquidGlassBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    controller: LiquidGlassController = LocalLiquidGlassController.current,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = visible) {
        onDismissRequest()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismissRequest() })
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(240)),
                exit = slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(200))
            ) {
                LiquidGlassContainer(
                    controller = controller,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .heightIn(max = 460.dp)
                        .navigationBarsPadding()
                        .pointerInput(Unit) {
                            // Consume taps inside sheet so they don't trigger backdrop dismiss
                            detectTapGestures(onTap = {})
                        },
                    testTag = "liquid_glass_bottom_sheet"
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Frosted Glass Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(42.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.38f))
                        )

                        // Sheet Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.72f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                headerAction?.invoke()
                                LiquidGlassIconButton(
                                    icon = Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    onClick = onDismissRequest,
                                    size = 32.dp,
                                    iconSize = 16.dp,
                                    controller = controller
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                        content()
                    }
                }
            }
        }
    }
}

/**
 * Glassmorphic Modal Dialog overlay rendered directly over the live video surface
 * so the blurred video frame remains dynamically visible behind the dialog.
 */
@Composable
fun LiquidGlassDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    headerTrailing: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    controller: LiquidGlassController = LocalLiquidGlassController.current,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = visible) {
        onDismissRequest()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.94f, animationSpec = tween(160))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismissRequest() })
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassContainer(
                controller = controller,
                shape = RoundedCornerShape(26.dp),
                contentPadding = PaddingValues(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .heightIn(max = 440.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {})
                    },
                testTag = "liquid_glass_dialog"
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        headerTrailing?.invoke()
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    // Scrollable Body
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Interactive real-time control sheet allowing users to dynamically tune the
 * Liquid Glass UI engine (`blurRadius`, `glassOpacity`, `tintColor`, and Presets)
 * while watching the video blur behind the controls in real time.
 */
@Composable
fun LiquidGlassCustomizerSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    controller: LiquidGlassController = LocalLiquidGlassController.current
) {
    val config by controller.config.collectAsState()

    LiquidGlassBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = "استوديو الزجاج السائل (Liquid Glass UI) 💧",
        subtitle = "تحكم فوري في التضبيب (RenderEffect Blur) والشفافية واللون فوق الفيديو الحي",
        headerAction = {
            TextButton(
                onClick = { controller.resetToDefaults() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("liquid_glass_reset_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("افتراضي", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        controller = controller
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Built-in Liquid Glass Presets
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                Text(
                    text = "الأنماط الزجاجية الجاهزة (Presets):",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassPreset.entries.forEach { preset ->
                    val isSelected = config.activePresetId == preset.id
                    LiquidGlassContainer(
                        controller = controller,
                        shape = RoundedCornerShape(14.dp),
                        isHighlighted = isSelected,
                        tintColorOverride = preset.tintColor,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        onClick = { controller.applyPreset(preset) },
                        testTag = "preset_${preset.id}"
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Column {
                                Text(
                                    text = preset.titleAr,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = "Blur ${preset.blurRadius.toInt()}px • ${(preset.glassOpacity * 100).toInt()}%",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. Real-Time Blur Radius Slider (0f .. 35f)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BlurOn, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "قوة التضبيب البلوري (Blur Radius):",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${String.format("%.1f", config.blurRadius)} px",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = config.blurRadius,
                    onValueChange = { controller.setBlurRadius(it) },
                    valueRange = LiquidGlassController.MIN_BLUR_RADIUS..LiquidGlassController.MAX_BLUR_RADIUS,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    ),
                    modifier = Modifier.testTag("slider_blur_radius")
                )
            }

            // 3. Real-Time Glass Opacity / Surface Alpha Slider (0.0f .. 1.0f)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Opacity, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "شفافية السطح الزجاجي (Glass Opacity):",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${(config.glassOpacity * 100).roundToInt()}%",
                        color = Color(0xFFFFD54F),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = config.glassOpacity,
                    onValueChange = { controller.setGlassOpacity(it) },
                    valueRange = LiquidGlassController.MIN_OPACITY..LiquidGlassController.MAX_OPACITY,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD54F),
                        activeTrackColor = Color(0xFFFFD54F),
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    ),
                    modifier = Modifier.testTag("slider_glass_opacity")
                )
            }

            // 4. Tint Color Swatches (Blended with live blurred video frame)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = Color(0xFFB388FF), modifier = Modifier.size(17.dp))
                Text(
                    text = "لون الصبغة الزجاجية (Tint Color):",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DefaultLiquidGlassTints.forEach { swatch ->
                    val isSelected = config.tintColor == swatch.color
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { controller.setTintColor(swatch.color) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("tint_${swatch.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(swatch.accentPreview.copy(alpha = 0.65f), swatch.color)
                                    )
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = swatch.labelAr,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = swatch.labelAr,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
