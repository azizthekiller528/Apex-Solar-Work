package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle as KyantRoundedRectangle
import com.kyant.shapes.RoundedRectangularShape
import kotlin.math.roundToInt

enum class LiquidGlassTone {
    CARD,
    DOCK,
    SUBTLE,
    ACCENT
}

/**
 * Provides the floating HUD [Backdrop] ONLY to components outside the `layerBackdrop(...)` subtree.
 * Never pass a scrolling-layer backdrop into a child inside the captured layer itself.
 */
val LocalHudLiquidBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * True when hardware RenderEffect + Backdrop live blur can run cleanly (Android 12+ / API 31+).
 */
fun isLiveBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val CapsuleSingleton: RoundedRectangularShape = Capsule()
private val Squircle20Singleton: RoundedRectangularShape = KyantRoundedRectangle(20.dp)
private val Squircle22Singleton: RoundedRectangularShape = KyantRoundedRectangle(22.dp)
private val Squircle24Singleton: RoundedRectangularShape = KyantRoundedRectangle(24.dp)
private val Squircle28Singleton: RoundedRectangularShape = KyantRoundedRectangle(28.dp)

val CapsuleShapeProvider: () -> Shape = { CapsuleSingleton }

fun liquidSquircleShape(radius: Dp = 24.dp): RoundedRectangularShape = when (radius) {
    20.dp -> Squircle20Singleton
    22.dp -> Squircle22Singleton
    24.dp -> Squircle24Singleton
    28.dp -> Squircle28Singleton
    else -> KyantRoundedRectangle(radius)
}

fun liquidCapsuleShape(): RoundedRectangularShape = CapsuleSingleton

private fun Shape.supportsLensEffect(): Boolean =
    this is RoundedRectangularShape || this is AbsoluteRoundedCornerShape || this is CornerBasedShape

object LiquidGlassTokens {
    private val cardLightBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.88f),
            Color(0xFFF3F8FF).copy(alpha = 0.76f),
            Color.White.copy(alpha = 0.70f)
        )
    )
    private val cardDarkBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF202E44).copy(alpha = 0.80f),
            Color(0xFF141D2E).copy(alpha = 0.74f),
            Color(0xFF0E1523).copy(alpha = 0.82f)
        )
    )
    private val dockLightBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.94f),
            Color(0xFFF3F8FF).copy(alpha = 0.86f),
            Color.White.copy(alpha = 0.88f)
        )
    )
    private val dockDarkBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF25354F).copy(alpha = 0.88f),
            Color(0xFF141E30).copy(alpha = 0.84f),
            Color(0xFF0E1524).copy(alpha = 0.90f)
        )
    )
    private val subtleLightBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.62f),
            Color(0xFFE7EFFC).copy(alpha = 0.48f)
        )
    )
    private val subtleDarkBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF283954).copy(alpha = 0.44f),
            Color(0xFF182436).copy(alpha = 0.36f)
        )
    )
    private val accentLightBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2E95FF),
            IosBlueLight,
            Color(0xFF0062CC)
        )
    )
    private val accentDarkBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF3395FF),
            IosBlueDark,
            Color(0xFF0066CC)
        )
    )

    private val accentBorder = BorderStroke(
        width = 1.2.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.35f)
            )
        )
    )
    private val subtleLightBorder = BorderStroke(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f),
                Color(0xFFC3D5EE).copy(alpha = 0.42f)
            )
        )
    )
    private val subtleDarkBorder = BorderStroke(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.04f)
            )
        )
    )
    private val dockLightBorder = BorderStroke(
        width = 1.2.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.98f),
                Color(0xFFCAD8EC).copy(alpha = 0.48f),
                Color.White.copy(alpha = 0.82f)
            )
        )
    )
    private val dockDarkBorder = BorderStroke(
        width = 1.2.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.10f),
                Color(0xFF60A5FA).copy(alpha = 0.20f)
            )
        )
    )
    private val cardLightBorder = BorderStroke(width = 1.dp, brush = dockLightBorder.brush)
    private val cardDarkBorder = BorderStroke(width = 1.dp, brush = dockDarkBorder.brush)

    fun surfaceGradient(tone: LiquidGlassTone, isDark: Boolean): Brush = when (tone) {
        LiquidGlassTone.CARD -> if (isDark) cardDarkBrush else cardLightBrush
        LiquidGlassTone.DOCK -> if (isDark) dockDarkBrush else dockLightBrush
        LiquidGlassTone.SUBTLE -> if (isDark) subtleDarkBrush else subtleLightBrush
        LiquidGlassTone.ACCENT -> if (isDark) accentDarkBrush else accentLightBrush
    }

    fun specularBorder(tone: LiquidGlassTone, isDark: Boolean): BorderStroke = when (tone) {
        LiquidGlassTone.ACCENT -> accentBorder
        LiquidGlassTone.SUBTLE -> if (isDark) subtleDarkBorder else subtleLightBorder
        LiquidGlassTone.DOCK -> if (isDark) dockDarkBorder else dockLightBorder
        LiquidGlassTone.CARD -> if (isDark) cardDarkBorder else cardLightBorder
    }
}

private class AmbientFieldCache(private val isDark: Boolean) {
    private var cachedWidth: Float = -1f
    private var cachedHeight: Float = -1f
    private val baseBrush: Brush = Brush.verticalGradient(
        if (isDark) listOf(Color(0xFF040710), Color(0xFF080E1C), Color(0xFF050812))
        else listOf(Color(0xFFE4EEFC), Color(0xFFF3F7FD), Color(0xFFE9F1FC))
    )
    private var c1 = Offset.Zero
    private var r1 = 1f
    private var b1: Brush? = null
    private var c2 = Offset.Zero
    private var r2 = 1f
    private var b2: Brush? = null
    private var c3 = Offset.Zero
    private var r3 = 1f
    private var b3: Brush? = null
    private var c4 = Offset.Zero
    private var r4 = 1f
    private var b4: Brush? = null

    fun draw(scope: DrawScope) {
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) return
        if (w != cachedWidth || h != cachedHeight || b1 == null) {
            cachedWidth = w
            cachedHeight = h
            c1 = Offset(w * 0.12f, h * 0.10f)
            r1 = w * 0.72f
            b1 = Brush.radialGradient(
                colors = listOf(
                    if (isDark) Color(0xFF2563EB).copy(alpha = 0.34f) else Color(0xFF60A5FA).copy(alpha = 0.36f),
                    Color.Transparent
                ),
                center = c1,
                radius = r1
            )
            c2 = Offset(w * 0.90f, h * 0.15f)
            r2 = w * 0.56f
            b2 = Brush.radialGradient(
                colors = listOf(
                    if (isDark) Color(0xFF0284C7).copy(alpha = 0.24f) else Color(0xFFFCD34D).copy(alpha = 0.28f),
                    Color.Transparent
                ),
                center = c2,
                radius = r2
            )
            c3 = Offset(w * 0.86f, h * 0.56f)
            r3 = w * 0.65f
            b3 = Brush.radialGradient(
                colors = listOf(
                    if (isDark) Color(0xFF4F46E5).copy(alpha = 0.22f) else Color(0xFF93C5FD).copy(alpha = 0.30f),
                    Color.Transparent
                ),
                center = c3,
                radius = r3
            )
            c4 = Offset(w * 0.18f, h * 0.88f)
            r4 = w * 0.62f
            b4 = Brush.radialGradient(
                colors = listOf(
                    if (isDark) Color(0xFF0D9488).copy(alpha = 0.18f) else Color(0xFF6EE7B7).copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = c4,
                radius = r4
            )
        }
        scope.drawRect(brush = baseBrush)
        b1?.let { scope.drawCircle(brush = it, center = c1, radius = r1) }
        b2?.let { scope.drawCircle(brush = it, center = c2, radius = r2) }
        b3?.let { scope.drawCircle(brush = it, center = c3, radius = r3) }
        b4?.let { scope.drawCircle(brush = it, center = c4, radius = r4) }
    }
}

private val LightAmbientCache = AmbientFieldCache(isDark = false)
private val DarkAmbientCache = AmbientFieldCache(isDark = true)

/**
 * Draws cached iOS-style ambient solar & glacial light field with zero per-frame
 * allocations during 120Hz scrolling.
 */
fun DrawScope.drawLiquidAmbientBackground(isDark: Boolean) {
    val cache = if (isDark) DarkAmbientCache else LightAmbientCache
    cache.draw(this)
}

@Composable
fun rememberAmbientCanvasBackdrop(isDark: Boolean = LocalIsDarkTheme.current): Backdrop =
    rememberCanvasBackdrop { drawLiquidAmbientBackground(isDark = isDark) }

private val DockLightShadow = Shadow(radius = 20.dp, color = Color(0xFF132D4E).copy(alpha = 0.14f))
private val DockDarkShadow = Shadow(radius = 20.dp, color = Color.Black.copy(alpha = 0.44f))
private val AccentInnerShadow = InnerShadow(radius = 8.dp, alpha = 0.24f)
private val DockHighlightLight = Highlight.Default.copy(width = 0.85.dp, alpha = 0.94f)
private val DockHighlightDark = Highlight.Default.copy(width = 0.85.dp, alpha = 0.72f)
private val CardHighlightLight = Highlight.Default.copy(width = 0.6.dp, alpha = 0.94f)
private val CardHighlightDark = Highlight.Default.copy(width = 0.6.dp, alpha = 0.72f)

/**
 * Real optical Liquid Glass powered by `Kyant0/AndroidLiquidGlass` (`drawBackdrop`):
 * vibrancy + live RenderEffect blur + AGSL lens refraction on Android 12/13+,
 * tuned for zero-allocation 120 FPS rendering.
 */
@Composable
fun Modifier.opticalLiquidGlass(
    backdrop: Backdrop,
    shape: () -> Shape = CapsuleShapeProvider,
    tone: LiquidGlassTone = LiquidGlassTone.DOCK,
    isDark: Boolean = LocalIsDarkTheme.current,
    blurRadius: Dp = 6.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 26.dp,
    chromaticAberration: Boolean = false,
    hasShadow: Boolean = true,
    pressProgress: Float = 0f
): Modifier {
    val liveBlur = isLiveBlurSupported()
    val lensSafe = shape().supportsLensEffect()

    val surfaceColor = when (tone) {
        LiquidGlassTone.CARD -> if (liveBlur) {
            if (isDark) Color(0xFF141D2E).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.62f)
        } else {
            if (isDark) Color(0xFF141D2E).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.90f)
        }

        LiquidGlassTone.DOCK -> if (liveBlur) {
            if (isDark) Color(0xFF101827).copy(alpha = 0.52f) else Color(0xFFFAFAFF).copy(alpha = 0.48f)
        } else {
            if (isDark) Color(0xFF101827).copy(alpha = 0.90f) else Color(0xFFFDFEFF).copy(alpha = 0.94f)
        }

        LiquidGlassTone.SUBTLE -> if (liveBlur) {
            if (isDark) Color(0xFF1D293F).copy(alpha = 0.36f) else Color.White.copy(alpha = 0.38f)
        } else {
            if (isDark) Color(0xFF1D293F).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.76f)
        }

        LiquidGlassTone.ACCENT -> {
            val base = if (isDark) IosBlueDark else IosBlueLight
            base.copy(alpha = if (liveBlur) 0.76f else 0.96f)
        }
    }

    val shadowValue = if (isDark) DockDarkShadow else DockLightShadow
    val shadowProvider: (() -> Shadow?)? = if (hasShadow) ({ shadowValue }) else null
    val highlightValue = when (tone) {
        LiquidGlassTone.DOCK, LiquidGlassTone.ACCENT -> if (isDark) DockHighlightDark else DockHighlightLight
        else -> if (isDark) CardHighlightDark else CardHighlightLight
    }

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = {
            vibrancy()
            if (blurRadius > 0.dp) {
                blur(blurRadius.toPx())
            }
            if (lensSafe && refractionHeight > 0.dp && refractionAmount > 0.dp) {
                lens(
                    refractionHeight = refractionHeight.toPx(),
                    refractionAmount = refractionAmount.toPx(),
                    depthEffect = true,
                    chromaticAberration = chromaticAberration
                )
            }
        },
        highlight = { highlightValue },
        shadow = shadowProvider,
        innerShadow = when {
            tone == LiquidGlassTone.ACCENT -> { { AccentInnerShadow } }
            pressProgress > 0f -> {
                {
                    InnerShadow(
                        radius = 8.dp,
                        alpha = 0.18f * pressProgress
                    )
                }
            }
            else -> null
        },
        layerBlock = if (pressProgress > 0f) {
            {
                val scale = 1f - 0.03f * pressProgress
                scaleX = scale
                scaleY = scale
            }
        } else {
            null
        },
        onDrawSurface = {
            if (tone == LiquidGlassTone.ACCENT) {
                val accentTint = if (isDark) IosBlueDark else IosBlueLight
                drawRect(accentTint, blendMode = BlendMode.Hue)
                drawRect(accentTint.copy(alpha = 0.74f))
            } else {
                drawRect(surfaceColor)
            }
        }
    )
}

/**
 * Lightweight frosted surface for elements nested inside fast-scrolling LazyColumns or cards,
 * keeping 60/120fps scrolling with clean specular rims and zero muddy internal shadow bleed.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = liquidSquircleShape(24.dp),
    tone: LiquidGlassTone = LiquidGlassTone.CARD,
    elevation: Dp = 0.dp,
    isDark: Boolean = LocalIsDarkTheme.current
): Modifier {
    val bgBrush = LiquidGlassTokens.surfaceGradient(tone = tone, isDark = isDark)
    val rimBorder = LiquidGlassTokens.specularBorder(tone = tone, isDark = isDark)
    return this
        .clip(shape)
        .background(brush = bgBrush, shape = shape)
        .border(border = rimBorder, shape = shape)
}

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkTheme.current,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawLiquidAmbientBackground(isDark = isDark) },
        content = content
    )
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = liquidSquircleShape(24.dp),
    tone: LiquidGlassTone = LiquidGlassTone.CARD,
    elevation: Dp = 0.dp,
    backdrop: Backdrop? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val clickableMod = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    val surfaceModifier = if (backdrop != null) {
        Modifier.opticalLiquidGlass(
            backdrop = backdrop,
            shape = { shape },
            tone = tone,
            isDark = isDark,
            blurRadius = if (tone == LiquidGlassTone.DOCK) 6.dp else 5.dp,
            refractionHeight = if (tone == LiquidGlassTone.DOCK) 14.dp else 10.dp,
            refractionAmount = if (tone == LiquidGlassTone.DOCK) 22.dp else 16.dp,
            chromaticAberration = false,
            hasShadow = elevation > 0.dp
        )
    } else {
        Modifier.liquidGlassSurface(
            shape = shape,
            tone = tone,
            elevation = elevation,
            isDark = isDark
        )
    }
    Box(
        modifier = modifier
            .then(surfaceModifier)
            .then(clickableMod),
        content = content
    )
}

@Composable
fun LiquidGlassPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: LiquidGlassTone = LiquidGlassTone.ACCENT,
    elevation: Dp = 8.dp,
    backdrop: Backdrop? = LocalHudLiquidBackdrop.current,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "pill_press"
    )
    val fg = when (tone) {
        LiquidGlassTone.ACCENT -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val surfaceMod = if (backdrop != null) {
        Modifier.opticalLiquidGlass(
            backdrop = backdrop,
            shape = CapsuleShapeProvider,
            tone = tone,
            isDark = isDark,
            blurRadius = 5.dp,
            refractionHeight = 14.dp,
            refractionAmount = 22.dp,
            chromaticAberration = tone == LiquidGlassTone.ACCENT,
            hasShadow = enabled && elevation > 0.dp,
            pressProgress = pressProgress
        )
    } else {
        Modifier
            .graphicsLayer {
                val scale = 1f - 0.03f * pressProgress
                scaleX = scale
                scaleY = scale
            }
            .liquidGlassSurface(
                shape = CircleShape,
                tone = tone,
                elevation = if (enabled) elevation else 0.dp,
                isDark = isDark
            )
    }

    Box(
        modifier = modifier
            .then(surfaceMod)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) fg else fg.copy(alpha = 0.48f)
        ) {
            Row(
                modifier = Modifier.padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

data class LiquidDockTabItem(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val DockActiveThumbDarkBrush = Brush.verticalGradient(
    listOf(IosBlueDark.copy(alpha = 0.30f), Color(0xFF1D4ED8).copy(alpha = 0.22f))
)
private val DockActiveThumbLightBrush = Brush.verticalGradient(
    listOf(IosBlueLight.copy(alpha = 0.16f), Color(0xFF60A5FA).copy(alpha = 0.10f))
)

/**
 * Interactive iOS 26 Liquid Glass Pill Dock powered by `Kyant0/AndroidLiquidGlass`:
 * live blurs & refracts scrolled content underneath, and features a spring-gliding
 * refractive droplet pill across tabs (tap or drag smoothly across the bar).
 */
@Composable
fun LiquidGlassTabDock(
    tabs: List<LiquidDockTabItem>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkTheme.current
) {
    if (tabs.isEmpty()) return
    var dragIndexOffset by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var isDragging by remember { mutableFloatStateOf(0f) }

    val targetPos = if (isDragging > 0.5f) dragIndexOffset else selectedIndex.toFloat()
    val animatedIndex by animateFloatAsState(
        targetValue = targetPos.coerceIn(0f, (tabs.size - 1).toFloat()),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
        label = "liquid_dock_slide"
    )

    BoxWithConstraints(
        modifier = modifier
            .height(64.dp)
            .opticalLiquidGlass(
                backdrop = backdrop,
                shape = CapsuleShapeProvider,
                tone = LiquidGlassTone.DOCK,
                isDark = isDark,
                blurRadius = 6.dp,
                refractionHeight = 16.dp,
                refractionAmount = 26.dp,
                chromaticAberration = false,
                hasShadow = true
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        val tabWidthDp = maxWidth / tabs.size
        val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val tabWidthPx = totalWidthPx / tabs.size

        // Spring-sliding optical droplet selector behind crisp tab icons & labels
        Box(
            modifier = Modifier
                .offset { IntOffset(x = (animatedIndex * tabWidthPx).roundToInt(), y = 0) }
                .width(tabWidthDp)
                .fillMaxHeight()
                .opticalLiquidGlass(
                    backdrop = backdrop,
                    shape = CapsuleShapeProvider,
                    tone = LiquidGlassTone.SUBTLE,
                    isDark = isDark,
                    blurRadius = 0.dp,
                    refractionHeight = 10.dp,
                    refractionAmount = 16.dp,
                    chromaticAberration = true,
                    hasShadow = false,
                    pressProgress = isDragging * 0.8f
                )
                .background(
                    brush = if (isDark) DockActiveThumbDarkBrush else DockActiveThumbLightBrush,
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) Color(0xFF60A5FA).copy(alpha = 0.45f) else IosBlueLight.copy(alpha = 0.28f),
                    shape = CircleShape
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tabs.size, totalWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            isDragging = 1f
                            dragIndexOffset = ((startOffset.x / totalWidthPx) * tabs.size - 0.5f)
                                .coerceIn(0f, (tabs.size - 1).toFloat())
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragIndexOffset = (dragIndexOffset + dragAmount / tabWidthPx)
                                .coerceIn(0f, (tabs.size - 1).toFloat())
                        },
                        onDragEnd = {
                            isDragging = 0f
                            val snapped = dragIndexOffset.roundToInt().coerceIn(0, tabs.size - 1)
                            onSelectIndex(snapped)
                        },
                        onDragCancel = {
                            isDragging = 0f
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { idx, tab ->
                val selected = idx == selectedIndex
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "dock_tab_tint"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { onSelectIndex(idx) }
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                        .testTag(tab.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        letterSpacing = (-0.2).sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = tint,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Detached circular iOS 26 optical Liquid Glass action button placed inline beside the Dock
 * so it never hovers over card action buttons.
 */
@Composable
fun LiquidGlassOrbButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tone: LiquidGlassTone = LiquidGlassTone.ACCENT,
    size: Dp = 64.dp,
    isDark: Boolean = LocalIsDarkTheme.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "orb_press"
    )
    Box(
        modifier = modifier
            .size(size)
            .opticalLiquidGlass(
                backdrop = backdrop,
                shape = { liquidCapsuleShape() },
                tone = tone,
                isDark = isDark,
                blurRadius = 8.dp,
                refractionHeight = 16.dp,
                refractionAmount = 26.dp,
                chromaticAberration = true,
                hasShadow = true,
                pressProgress = pressProgress
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (tone == LiquidGlassTone.ACCENT) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(25.dp)
        )
    }
}

@Composable
fun LiquidGlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val isDark = LocalIsDarkTheme.current
    val bgBrush = if (selected) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    IosBlueDark.copy(alpha = 0.34f),
                    Color(0xFF1E40AF).copy(alpha = 0.30f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    IosBlueLight.copy(alpha = 0.16f),
                    Color(0xFF60A5FA).copy(alpha = 0.14f)
                )
            )
        }
    } else {
        LiquidGlassTokens.surfaceGradient(tone = LiquidGlassTone.SUBTLE, isDark = isDark)
    }

    val borderStroke = if (selected) {
        BorderStroke(
            1.dp,
            if (isDark) Color(0xFF60A5FA).copy(alpha = 0.55f) else IosBlueLight.copy(alpha = 0.42f)
        )
    } else {
        LiquidGlassTokens.specularBorder(tone = LiquidGlassTone.SUBTLE, isDark = isDark)
    }

    val fgColor by animateColorAsState(
        targetValue = if (selected) {
            if (isDark) Color(0xFF93C5FD) else Color(0xFF005EC4)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "glass_chip_fg"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(brush = bgBrush, shape = CircleShape)
            .border(border = borderStroke, shape = CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        CompositionLocalProvider(LocalContentColor provides fgColor) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = fgColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LiquidGlassSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(shape = CircleShape, tone = LiquidGlassTone.SUBTLE, isDark = isDark)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { idx, title ->
            val selected = idx == selectedIndex
            val thumbBg = if (selected) {
                if (isDark) {
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF283A57).copy(alpha = 0.94f),
                            Color(0xFF19273D).copy(alpha = 0.92f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.98f), Color(0xFFF4F8FF).copy(alpha = 0.94f))
                    )
                }
            } else {
                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            }
            val thumbBorder = if (selected) {
                LiquidGlassTokens.specularBorder(LiquidGlassTone.DOCK, isDark)
            } else {
                BorderStroke(0.dp, Color.Transparent)
            }
            val textColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "seg_tab_fg"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(brush = thumbBg, shape = CircleShape)
                    .then(if (selected) Modifier.border(thumbBorder, CircleShape) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(idx) }
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
