package com.v2ir.ui.screens.configs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.v2ir.ui.theme.CloudflareOrange

@Composable
fun CloudflareScannerSection(
    onStartScan: () -> Unit
) {
    Surface(
        onClick = onStartScan,
        color = CloudflareOrange.copy(alpha = 0.1f),
        shape = CircleShape,
        modifier = Modifier.size(48.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CloudflareOrange.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Cloudflare Scanner",
                tint = CloudflareOrange,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}




