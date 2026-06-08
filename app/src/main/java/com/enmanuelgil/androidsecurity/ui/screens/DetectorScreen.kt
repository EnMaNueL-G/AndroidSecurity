package com.enmanuelgil.androidsecurity.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.model.*
import com.enmanuelgil.androidsecurity.ui.components.*
import com.enmanuelgil.androidsecurity.ui.viewmodel.DetectorViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DetectorScreen(vm: DetectorViewModel = viewModel()) {
    val context  = LocalContext.current
    val threats  by vm.threats.collectAsState()
    val loading  by vm.loading.collectAsState()

    LaunchedEffect(Unit) { vm.load(context) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title    = stringResource(R.string.det_title),
            subtitle = stringResource(R.string.det_subtitle)
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        val nonSystemThreats = threats.filter { !it.isSystem }
        val systemThreats    = threats.filter { it.isSystem }

        if (threats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = Color(0xFF3DDC84), modifier = Modifier.size(56.dp))
                    Text(stringResource(R.string.det_no_threats),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF3DDC84))
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Summary
            item {
                Surface(
                    color = Color(0xFFEF5B5B).copy(0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFEF5B5B), modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                "${nonSystemThreats.size} elementos sospechosos",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFEF5B5B)
                            )
                            if (systemThreats.isNotEmpty())
                                Text(
                                    "${systemThreats.size} del sistema (normales)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                )
                        }
                    }
                }
            }

            // Non-system threats first
            if (nonSystemThreats.isNotEmpty()) {
                item {
                    Text(
                        "Apps de terceros",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(nonSystemThreats, key = { "${it.packageName}-${it.threatType}" }) { threat ->
                    ThreatCard(threat = threat, onOpenSettings = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", threat.packageName, null))
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    })
                }
            }

            // System entries (collapsed style)
            if (systemThreats.isNotEmpty()) {
                item {
                    Text(
                        "Sistema (informativos)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(systemThreats, key = { "${it.packageName}-${it.threatType}-sys" }) { threat ->
                    ThreatCard(threat = threat, dimmed = true, onOpenSettings = {})
                }
            }
        }
    }
}

@Composable
private fun ThreatCard(
    threat        : ThreatEntry,
    dimmed        : Boolean = false,
    onOpenSettings: () -> Unit
) {
    val alpha = if (dimmed) 0.5f else 1f
    val typeLabel = when (threat.threatType) {
        ThreatType.ACCESSIBILITY_SERVICE -> "Accesibilidad"
        ThreatType.DANGEROUS_COMBO       -> "Permisos peligrosos"
        ThreatType.OVERLAY_ACTIVE        -> "Overlay"
        ThreatType.DEVICE_ADMIN          -> "Admin dispositivo"
    }
    val typeColor = when (threat.riskLevel) {
        RiskLevel.HIGH   -> Color(0xFFEF5B5B)
        RiskLevel.MEDIUM -> Color(0xFFF59E0B)
        else             -> MaterialTheme.colorScheme.onSurface.copy(0.4f)
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(drawable = threat.icon, size = 40.dp, alpha = alpha)

            Column(Modifier.weight(1f)) {
                Text(
                    threat.appName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha)
                    ),
                    maxLines = 1
                )
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = typeColor.copy(0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            typeLabel,
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = typeColor.copy(alpha)
                        )
                    }
                    if (threat.isSystem) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
                            Text("Sistema", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                    }
                }
            }

            if (!dimmed) {
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.OpenInNew, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
