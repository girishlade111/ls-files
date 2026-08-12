package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class IconAnimationType {
    SPRING_SCALE,
    ROTATE_ON_TOGGLE,
    BOUNCE,
    PULSE
}

/**
 * An animated Icon component that applies spring scale or rotation whenever state changes.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    isSelected: Boolean = false,
    animationType: IconAnimationType = IconAnimationType.SPRING_SCALE
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "animated_icon_scale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isSelected && animationType == IconAnimationType.ROTATE_ON_TOGGLE) 15f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "animated_icon_rotation"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .scale(scale)
            .rotate(rotation)
    )
}

/**
 * An IconButton with an interactive spring press bounce and smooth icon transition.
 */
@Composable
fun AnimatedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "icon_btn_press_scale"
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.scale(pressScale)
    ) {
        content()
    }
}

/**
 * Smoothly morphs/crossfades between two ImageVectors with spring rotation and scale.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedToggleIcon(
    activeVector: ImageVector,
    inactiveVector: ImageVector,
    isActive: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 24.dp
) {
    val rotation by animateFloatAsState(
        targetValue = if (isActive) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "toggle_icon_rotation"
    )

    val targetVector = if (isActive) activeVector else inactiveVector

    AnimatedContent(
        targetState = targetVector,
        transitionSpec = {
            (scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    fadeIn(animationSpec = tween(200)))
                .togetherWith(
                    scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                            fadeOut(animationSpec = tween(200))
                )
        },
        label = "toggle_icon_crossfade"
    ) { icon ->
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
                .size(iconSize)
                .rotate(rotation)
        )
    }
}

/**
 * An Icon used in Navigation Bars or Navigation Drawers that animates when selected.
 */
@Composable
fun AnimatedNavIcon(
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.18f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_icon_scale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isSelected) 360f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "nav_icon_rotate"
    )

    val iconToDraw = if (isSelected) selectedIcon else unselectedIcon

    Icon(
        imageVector = iconToDraw,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .scale(scale)
            .rotate(rotation)
    )
}

/**
 * An Icon that spins endlessly or when triggered (e.g. Syncing, Refreshing, Auto-purge).
 */
@Composable
fun AnimatedSpinningIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    isSpinning: Boolean = true,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin_transition")
    val rotation by if (isSpinning) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spin_rotation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.rotate(rotation)
    )
}

/**
 * An Icon that pulses subtly to draw attention (e.g., Safe Folder, Security, Alerts).
 */
@Composable
fun AnimatedPulseIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    isPulsing: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by if (isPulsing) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.scale(pulseScale)
    )
}

/**
 * Interactive category icon with a spring bounce response on touch.
 */
@Composable
fun AnimatedCategoryIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 28.dp
) {
    var isHoveredOrClicked by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHoveredOrClicked) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = { isHoveredOrClicked = false },
        label = "category_icon_scale"
    )

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { isHoveredOrClicked = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .scale(scale)
        )
    }
}
