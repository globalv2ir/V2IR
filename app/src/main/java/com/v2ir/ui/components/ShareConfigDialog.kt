package com.v2ir.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.v2ir.R
import com.v2ir.ui.theme.NeonCyan
import com.v2ir.ui.theme.TextHint
import com.v2ir.ui.theme.TextSecondary

@Composable
fun ShareConfigDialog(
    configName: String,
    shareText: String,
    qrBitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition(label = "dialog_neon")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderRotation"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Apply Window Blur if on Android 12+ (API 31+)
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(80)
                window.setDimAmount(0.25f) // Lighter dimming for glass effect
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Color.Transparent 
                    else Color.Black.copy(alpha = 0.55f)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .drawBehind {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val colors = intArrayOf(
                            NeonCyan.toArgb(),
                            Color.Transparent.toArgb(),
                            NeonCyan.copy(alpha = 0.5f).toArgb(),
                            Color.Transparent.toArgb(),
                            NeonCyan.toArgb()
                        )
                        val shader = android.graphics.SweepGradient(centerX, centerY, colors, null)
                        val matrix = android.graphics.Matrix()
                        matrix.postRotate(rotation, centerX, centerY)
                        shader.setLocalMatrix(matrix)

                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            this.shader = shader
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 3.dp.toPx()
                        }

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawRoundRect(
                                0f, 0f, size.width, size.height,
                                28.dp.toPx(), 28.dp.toPx(),
                                paint
                            )
                        }
                    }
                    .padding(2.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    backgroundAlpha = 0.22f,
                    borderColor = NeonCyan.copy(alpha = 0.2f),
                    blurRadius = 25.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.configs_share_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = NeonCyan,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = configName,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.9f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(210.dp)
                                    .background(Color.White, RoundedCornerShape(16.dp))
                                    .padding(10.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = shareText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    copyToClipboard(context, shareText)
                                    Toast.makeText(context, R.string.configs_export_success, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(R.string.configs_share_copy), fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(R.string.configs_share), fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_close), color = TextHint)
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("config", text))
}




