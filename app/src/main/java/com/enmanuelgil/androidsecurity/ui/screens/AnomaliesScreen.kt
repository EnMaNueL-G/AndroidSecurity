package com.enmanuelgil.androidsecurity.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.androidsecurity.model.AnomalyEntry
import com.enmanuelgil.androidsecurity.model.RiskLevel
import com.enmanuelgil.androidsecurity.ui.components.ScreenHeader
import com.enmanuelgil.androidsecurity.ui.viewmodel.AnomaliesViewModel

@Composable
fun AnomaliesScreen(vm: AnomaliesViewModel = viewModel()) {
    val context = LocalContext.current
    val state   by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load(context) }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            ScreenHeader(
                title    = "Anomalías del dispositivo",
                subtitle = "Detección de vectores de intervención, acceso remoto y modificaciones críticas"
            )
        }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            return@LazyColumn
        }

        // Summary banner
        item {
            val highCount = state.anomalies.count { it.riskLevel == RiskLevel.HIGH }
            val medCount  = state.anomalies.count { it.riskLevel == RiskLevel.MEDIUM }
            val banner    = when {
                highCount > 0  -> Color(0xFFEF5B5B)
                medCount  > 0  -> Color(0xFFF59E0B)
                else           -> Color(0xFF3DDC84)
            }
            val msg = when {
                highCount > 0  -> "$highCount riesgo(s) ALTO detectado(s) — acción recomendada"
                medCount  > 0  -> "$medCount riesgo(s) medio detectado(s) — revisar"
                else           -> "No se detectaron anomalías críticas"
            }
            Surface(
                color    = banner.copy(0.12f),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (highCount > 0 || medCount > 0) Icons.Default.Warning
                        else Icons.Default.CheckCircle,
                        null, tint = banner, modifier = Modifier.size(22.dp)
                    )
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = banner)
                }
            }
        }

        if (state.anomalies.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Color(0xFF3DDC84), modifier = Modifier.size(56.dp))
                        Text("Dispositivo limpio", style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF3DDC84))
                        Text("No se detectaron métodos de intervención o anomalías.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                    }
                }
            }
        } else {
            items(state.anomalies, key = { it.type.name }) { anomaly ->
                AnomalyCard(
                    anomaly   = anomaly,
                    onAction  = { scheme ->
                        try { context.startActivity(Intent(scheme)) }
                        catch (_: Exception) {}
                    }
                )
            }
        }
    }
}

@Composable
private fun AnomalyCard(
    anomaly  : AnomalyEntry,
    onAction : (String) -> Unit
) {
    val riskColor = when (anomaly.riskLevel) {
        RiskLevel.HIGH   -> Color(0xFFEF5B5B)
        RiskLevel.MEDIUM -> Color(0xFFF59E0B)
        else             -> Color(0xFF3DDC84)
    }
    val riskLabel = when (anomaly.riskLevel) {
        RiskLevel.HIGH   -> "Alto"
        RiskLevel.MEDIUM -> "Medio"
        else             -> "Bajo"
    }
    val riskIcon = when (anomaly.riskLevel) {
        RiskLevel.HIGH   -> Icons.Default.GppBad
        RiskLevel.MEDIUM -> Icons.Default.GppMaybe
        else             -> Icons.Default.Info
    }

    var expanded by remember { mutableStateOf(anomaly.riskLevel == RiskLevel.HIGH) }

    Card(
        onClick   = { expanded = !expanded },
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = riskColor.copy(0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(riskIcon, null, tint = riskColor, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(anomaly.title,
                        style = MaterialTheme.typography.bodyMedium)
                    Surface(color = riskColor.copy(0.10f), shape = RoundedCornerShape(4.dp)) {
                        Text(riskLabel,
                            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = riskColor)
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint     = MaterialTheme.colorScheme.onSurface.copy(0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                Spacer(Modifier.height(10.dp))
                Text(
                    anomaly.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
                if (anomaly.actionText.isNotEmpty() && anomaly.actionScheme.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onAction(anomaly.actionScheme) },
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = riskColor.copy(0.15f),
                            contentColor   = riskColor
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(anomaly.actionText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
