package com.v2ir.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ir.R
import com.v2ir.ui.theme.NeonCyan
import com.v2ir.ui.theme.NeonGreen
import com.v2ir.ui.theme.TextHint

@Composable
fun LiveTrafficGraph(
    downloadHistory: List<Float>,
    uploadHistory: List<Float>,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        backgroundAlpha = 0.10f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.home_live_graph),
                style = MaterialTheme.typography.labelSmall.copy(color = TextHint)
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val maxPoints = 30
                val dl = downloadHistory.takeLast(maxPoints)
                val ul = uploadHistory.takeLast(maxPoints)
                val maxVal = (dl + ul).maxOrNull()?.coerceAtLeast(1f) ?: 1f
                val stepX = size.width / (maxPoints - 1).coerceAtLeast(1)

                fun buildPath(values: List<Float>): Path {
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = index * stepX
                        val y = size.height - (value / maxVal) * size.height * 0.9f
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    return path
                }

                if (dl.isNotEmpty()) {
                    drawPath(
                        path = buildPath(dl),
                        color = NeonCyan.copy(alpha = 0.8f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }
                if (ul.isNotEmpty()) {
                    drawPath(
                        path = buildPath(ul),
                        color = NeonGreen.copy(alpha = 0.7f),
                        style = Stroke(width = 2f, cap = StrokeCap.Round)
                    )
                }

                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
        }
    }
}




