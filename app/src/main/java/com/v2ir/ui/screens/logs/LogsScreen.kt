package com.v2ir.ui.screens.logs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.v2ir.R
import com.v2ir.data.model.LogEntry
import com.v2ir.data.model.LogLevel
import com.v2ir.ui.components.GlassCard
import com.v2ir.ui.theme.LogError
import com.v2ir.ui.theme.LogInfo
import com.v2ir.ui.theme.LogWarning
import com.v2ir.ui.theme.NeonCyan
import com.v2ir.ui.theme.TextHint
import com.v2ir.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(viewModel: LogsViewModel = hiltViewModel()) {
    val logs by viewModel.logs.collectAsState()
    val filterLevel by viewModel.filter.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = stringResource(R.string.logs_title), style = MaterialTheme.typography.headlineMedium.copy(color = NeonCyan))
                Text(text = stringResource(R.string.logs_subtitle), style = MaterialTheme.typography.labelSmall.copy(color = TextHint))
            }
            Row {
                IconButton(onClick = {
                    if (viewModel.copyToClipboard()) {
                        Toast.makeText(context, context.getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.logs_copy), tint = TextSecondary)
                }
                IconButton(onClick = { viewModel.clearLogs() }) {
                    Icon(Icons.Default.ClearAll, contentDescription = stringResource(R.string.logs_clear), tint = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogFilterChip(stringResource(R.string.logs_filter_all), filterLevel == null, { viewModel.setFilter(null) }, NeonCyan)
            LogFilterChip(stringResource(R.string.logs_filter_info), filterLevel == LogLevel.INFO, { viewModel.setFilter(LogLevel.INFO) }, LogInfo)
            LogFilterChip(stringResource(R.string.logs_filter_warning), filterLevel == LogLevel.WARNING, { viewModel.setFilter(LogLevel.WARNING) }, LogWarning)
            LogFilterChip(stringResource(R.string.logs_filter_error), filterLevel == LogLevel.ERROR, { viewModel.setFilter(LogLevel.ERROR) }, LogError)
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            backgroundAlpha = 0.10f
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.logs_empty), style = MaterialTheme.typography.bodyMedium.copy(color = TextHint))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { it.id }) { log -> LogRow(log) }
                }
            }
        }
    }
}

@Composable
private fun LogFilterChip(label: String, selected: Boolean, onClick: () -> Unit, color: Color) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color,
            containerColor = Color.White.copy(alpha = 0.08f),
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = color.copy(alpha = 0.5f),
            borderColor = Color.White.copy(alpha = 0.2f)
        )
    )
}

@Composable
private fun LogRow(log: LogEntry) {
    val (levelColor, levelIcon) = when (log.level) {
        LogLevel.INFO -> LogInfo to Icons.Default.Info
        LogLevel.WARNING -> LogWarning to Icons.Default.Warning
        LogLevel.ERROR -> LogError to Icons.Default.ErrorOutline
        LogLevel.SYSTEM -> com.v2ir.ui.theme.NeonCyan to Icons.Default.Settings
    }
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = sdf.format(Date(log.timestamp))

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(levelIcon, contentDescription = null, tint = levelColor, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeStr, style = MaterialTheme.typography.labelSmall.copy(color = TextHint, fontFamily = FontFamily.Monospace))
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(levelColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(log.tag, style = MaterialTheme.typography.labelSmall.copy(color = levelColor, fontFamily = FontFamily.Monospace))
                }
            }
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            )
        }
    }
}




