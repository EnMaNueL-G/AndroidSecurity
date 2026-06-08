package com.enmanuelgil.androidsecurity.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.enmanuelgil.androidsecurity.model.PermCategory

// ── ScreenHeader ──────────────────────────────────────────────────────────────
@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.padding(top = 2.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(0.2f)
        )
    }
}

// ── AppIcon ───────────────────────────────────────────────────────────────────
@Composable
fun AppIcon(drawable: Drawable?, size: Dp = 40.dp, alpha: Float = 1f) {
    val bitmap = remember(drawable) {
        try { drawable?.toBitmap()?.asImageBitmap() } catch (e: Exception) { null }
    }
    if (bitmap != null) {
        Image(
            bitmap   = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .graphicsLayer { this.alpha = alpha }
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Android, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha),
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

// ── CategoryBadge ─────────────────────────────────────────────────────────────
@Composable
fun CategoryBadge(category: PermCategory) {
    val (color, icon) = when (category) {
        PermCategory.CAMERA       -> Color(0xFF0EA5E9)  to Icons.Default.Videocam
        PermCategory.MICROPHONE   -> Color(0xFFEF5B5B)  to Icons.Default.Mic
        PermCategory.LOCATION     -> Color(0xFFF59E0B)  to Icons.Default.LocationOn
        PermCategory.CONTACTS     -> Color(0xFF7C5CEF)  to Icons.Default.Contacts
        PermCategory.SMS          -> Color(0xFFEF5B5B)  to Icons.Default.Sms
        PermCategory.STORAGE      -> Color(0xFF3DDC84)  to Icons.Default.Storage
        PermCategory.ACCESSIBILITY -> Color(0xFFF59E0B) to Icons.Default.AccessibilityNew
        PermCategory.DEVICE_ADMIN -> Color(0xFFEF5B5B)  to Icons.Default.AdminPanelSettings
        PermCategory.OVERLAY      -> Color(0xFFF59E0B)  to Icons.Default.Layers
        PermCategory.OTHER        -> Color(0xFF64748B)  to Icons.Default.Circle
    }
    Surface(
        color = color.copy(0.12f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Icon(
            icon, null,
            tint = color,
            modifier = Modifier
                .padding(4.dp)
                .size(14.dp)
        )
    }
}

// ── StatChip ──────────────────────────────────────────────────────────────────
@Composable
fun StatChip(value: String, label: String, color: Color) {
    Surface(
        color = color.copy(0.1f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold, color = color
            ))
            Text(label, style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            ))
        }
    }
}
