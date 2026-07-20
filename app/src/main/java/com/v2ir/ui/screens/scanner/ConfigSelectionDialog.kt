package com.v2ir.ui.screens.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.v2ir.data.model.Config
import com.v2ir.ui.theme.NeonCyan
import com.v2ir.ui.theme.TextSecondary

@Composable
fun ConfigSelectionDialog(
    configs: List<Config>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val selectedIds = remember { mutableStateListOf<Long>() }
    val cloudflareConfigs = configs.filter { it.isCloudflare }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب کانفیگ‌های کلودفلر", color = Color.White) },
        text = {
            Column {
                Text(
                    "آی‌پی جدید روی کانفیگ‌های انتخاب شده اعمال خواهد شد.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                if (cloudflareConfigs.isEmpty()) {
                    Text("هیچ کانفیگ کلودفلری یافت نشد.", color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(cloudflareConfigs) { config ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedIds.contains(config.id)) selectedIds.remove(config.id)
                                        else selectedIds.add(config.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedIds.contains(config.id)) 
                                        Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (selectedIds.contains(config.id)) NeonCyan else Color.Gray
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(config.name, color = Color.White)
                                    Text(config.address, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("تأیید و اعمال", color = NeonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = Color.White)
            }
        },
        containerColor = Color(0xFF1A1F2B)
    )
}




