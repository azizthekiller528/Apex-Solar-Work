package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class LiquidGlassTone {
    CARD,
    DOCK,
    SUBTLE,
    ACCENT
}

object LiquidGlassTokens {
    fun surfaceGradient(tone: LiquidGlassTone, isDark: Boolean): Brush = when (tone) {
        LiquidGlassTone.CARD -> if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF212F46).copy(alpha = 0.78f),
                    Color(0xFF141D2E).copy(alpha = 0.72f),
                    Color(0xFF0E1523).copy(alpha = 0.80f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.88f),
                    Color(0xFFF4F8FF).copy(alpha = 0.76f),
                    Color.White.copy(alpha = 0.70f)
                )
            )
        }

        LiquidGlassTone.DOCK -> if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF25354F).copy(alpha = 0.88f),
                    Color(0xFF141E30).copy(alpha = 0.84f),
                    Color(0xFF0E1524).copy(alpha = 0.90f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.94f),
                    Color(0xFFF3F8FF).copy(alpha = 0.86f),
                    Color.White.copy(alpha = 0.88f)
                )
            )
        }

        LiquidGlassTone.SUBTLE -> if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF283954).copy(alpha = 0.44f),
                    Color(0xFF182436).copy(alpha = 0.36f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.62f),
                    Color(0xFFE7EFFC).copy(alpha = 0.48f)
                )
            )
        }

        LiquidGlassTone.ACCENT -> if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF3395FF),
                    IosBlueDark,
                    Color(0xFF0066CC)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF2E95FF),
                    IosBlueLight,
                    Color(0xFF0062CC)
                )
            )
        }
    }

    fun specularBorder(tone: LiquidGlassTone, isDark: Boolean): BorderStroke {
        val width = if (tone == LiquidGlassTone.DOCK || tone == LiquidGlassTone.ACCENT) 1.2.dp else 1.dp
        val brush = when (tone) {
            LiquidGlassTone.ACCENT -> Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.65f),
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.35f)
                )
            )

            LiquidGlassTone.SUBTLE -> if (isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color(0xFFC3D5EE).copy(alpha = 0.42f)
                    )
                )
            }

            else -> if (isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color.White.copy(alpha = 0.10f),
                        Color(0xFF60A5FA).copy(alpha = 0.20f)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f),
                        Color(0xFFCAD8EC).copy(alpha = 0.48f),
                        Color.White.copy(alpha = 0.82f)
                    )
                )
            }
        }
        return BorderStroke(width = width, brush = brush)
    }
}

@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    tone: LiquidGlassTone = LiquidGlassTone.CARD,
    elevation: Dp = 0.dp,
    isDark: Boolean = LocalIsDarkTheme.current
): Modifier {
    val bgBrush = LiquidGlassTokens.surfaceGradient(tone = tone, isDark = isDark)
    val rimBorder = LiquidGlassTokens.specularBorder(tone = tone, isDark = isDark)
    val shadowMod = if (elevation > 0.dp) {
        val shadowTint = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0xFF1E3A5F).copy(alpha = 0.14f)
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = shadowTint,
            spotColor = shadowTint
        )
    } else {
        Modifier
    }
    return this
        .then(shadowMod)
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
            .drawBehind {
                val baseColors = if (isDark) {
                    listOf(Color(0xFF040710), Color(0xFF080E1C), Color(0xFF050812))
                } else {
                    listOf(Color(0xFFE7EFFB), Color(0xFFF3F7FD), Color(0xFFEBF2FC))
                }
                drawRect(brush = Brush.verticalGradient(baseColors))

                // Top-left glacial sapphire light orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFF1D4ED8).copy(alpha = 0.30f) else Color(0xFF7DD3FC).copy(alpha = 0.34f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.14f, size.height * 0.10f),
                        radius = size.width * 0.72f
                    ),
                    center = Offset(size.width * 0.14f, size.height * 0.10f),
                    radius = size.width * 0.72f
                )

                // Top-right warm solar / glacial cyan orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFF0284C7).copy(alpha = 0.22f) else Color(0xFFFDE68A).copy(alpha = 0.24f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.90f, size.height * 0.16f),
                        radius = size.width * 0.58f
                    ),
                    center = Offset(size.width * 0.90f, size.height * 0.16f),
                    radius = size.width * 0.58f
                )

                // Mid-right liquid sapphire bloom
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFF4338CA).copy(alpha = 0.20f) else Color(0xFF93C5FD).copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.56f),
                        radius = size.width * 0.65f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.56f),
                    radius = size.width * 0.65f
                )

                // Bottom-left aurora teal refraction glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFF0D9488).copy(alpha = 0.16f) else Color(0xFFA7F3D0).copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.88f),
                        radius = size.width * 0.62f
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.88f),
                    radius = size.width * 0.62f
                )
            },
        content = content
    )
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tone: LiquidGlassTone = LiquidGlassTone.CARD,
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickableMod = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .liquidGlassSurface(shape = shape, tone = tone, elevation = elevation)
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
    elevation: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val fg = when (tone) {
        LiquidGlassTone.ACCENT -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = CircleShape,
                tone = tone,
                elevation = if (enabled) elevation else 0.dp,
                isDark = isDark
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
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
                    .then(
                        if (selected) {
                            Modifier.shadow(
                                elevation = if (isDark) 4.dp else 6.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.12f)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(CircleShape)
                    .background(brush = thumbBg, shape = CircleShape)
                    .then(if (selected) Modifier.border(thumbBorder, CircleShape) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(idx) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
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
