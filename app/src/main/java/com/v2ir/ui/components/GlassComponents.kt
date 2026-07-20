package com.v2ir.ui.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ir.R

private val GlassNeonBorder = Color.White.copy(alpha = 0.25f)

@Composable
fun StaticAppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.app_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f)
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    StaticAppBackground(modifier = modifier, content = content)
}

/**
 * A Glassmorphism card. 
 * Note: Modifier.blur blurs the content of the node. 
 * To prevent text from being blurry, we apply blur only to a background layer.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 0.5.dp,
    borderColor: Color = GlassNeonBorder,
    backgroundAlpha: Float = 0.18f,
    blurRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = shape
            )
    ) {
        // Blurred background layer
        // On Android 12+, we can use blur on a separate node to avoid blurring the text
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = backgroundAlpha))
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
                        Modifier.blur(blurRadius)
                    } else {
                        Modifier
                    }
                )
        )
        
        // Content layer (Top)
        Box(modifier = Modifier) {
            content()
        }
    }
}

@Composable
fun GlassButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = GlassNeonBorder,
                shape = shape
            ),
        content = content
    )
}




