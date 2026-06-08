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
import androidx.compose.ui.unit.dp
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.model.*
import com.enmanuelgil.androidsecurity.ui.components.*
import com.enmanuelgil.androidsecurity.ui.viewmodel.DetectorViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DetectorScreen(vm: DetectorViewModel = viewModel()) {
    val context = LocalContext.current
    val threats by vm.threats.collectAsState()
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { vm.load(context) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title    = stringResource(R.string.det_title),
            subtitle = stringResource(R.string.det_subtitle)
        )

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            threats.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Color(0xFF3DDC84), modifier = Modifier.size(56.dp))
                        Text(stringResource(R.string.det_no_threats),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF3DDC84))
                        Text("No se detectaron combinaciones peligrosas de permisos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            }

            else -> {
                val nonSystemThreats = threats.filter { !it.isSystem }
                val systemThreats    = threats.filter { it.isSystem }

                LazyColumn(
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Summary banner
                    item {
                        Surface(
                            color = Color(0xFFEF5B5B).copy(0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment    = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Warning, null,
                                    tint = Color(0xFFEF5B5B), modifier = Modifier.size(24.dp))
                                Column {
                                    Text(
                                        "${nonSystemThreats.size} elemento(s) sospechoso(s)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFEF5B5B)
                                    )
                                    if (systemThreats.isNotEmpty()) {
                                        Text(
                                            "${systemThreats.size} del sistema (informativos)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // User-app threats
                    if (nonSystemThreats.isNotEmpty()) {
                        item {
                            Text("Apps de terceros",
                                style    = MaterialTheme.typography.labelMedium,
                                color    = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                        items(nonSystemThreats,
                            key = { "${it.packageName}-${it.threatType}" }) { threat ->
                            ThreatCard(threat = threat, onOpenSettings = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", threat.packageName, null))
                                    )
                                } catch (_: Exception) {}
                            })
                        }
                    }

                    // System (informative only)
                    if (systemThreats.isNotEmpty()) {
                        item {
                            Text("Sistema (informativos)",
                                style    = MaterialTheme.typography.labelMedium,
                                color    = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                        items(systemThreats,
                            key = { "${it.packageName}-${it.threatType}-sys" }) { threat ->
                            ThreatCard(threat = threat, dimmed = true, onOpenSettings = {})
                        }
                    }
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
    val alpha = if (dimmed) 0.45f else 1f

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

    var expanded by remember { mutableStateOf(!dimmed) }

    Card(
        onClick   = { if (!dimmed) expanded = !expanded },
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(drawable = threat.icon, size = 40.dp, alpha = alpha)

                Column(Modifier.weight(1f)) {
                    Text(threat.appName,
                        style    = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha)
                        ),
                        maxLines = 1)

                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Surface(color = typeColor.copy(0.12f), shape = RoundedCornerShape(4.dp)) {
                            Text(typeLabel,
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = typeColor.copy(alpha))
                        }
                        if (threat.isSystem) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Sistema",
                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            }
                        }
                    }
                }

                if (!dimmed) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.OpenInNew, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Reason — Spanish description ──────────────
            if (!dimmed && threat.reason.isNotEmpty() && expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                Spacer(Modifier.height(8.dp))
                Surface(
                    color    = typeColor.copy(0.07f),
                    shape    = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null,
                            tint = typeColor, modifier = Modifier.size(16.dp))
                        Text(
                            threat.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.75f)
                        )
                    }
                }
            }
            if (!dimmed && threat.detail.isNotEmpty() && expanded) {
                Spacer(Modifier.height(4.dp))
                Text(threat.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
    }
}
