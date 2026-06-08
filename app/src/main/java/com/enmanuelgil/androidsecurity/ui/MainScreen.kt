package com.enmanuelgil.androidsecurity.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.ui.screens.*

enum class Screen { PERMISSIONS, DETECTOR, GUARD, ANOMALIES, HUELLA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(initialScreen: Screen = Screen.PERMISSIONS) {
    var selected by remember { mutableStateOf(initialScreen) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selected == Screen.PERMISSIONS,
                    onClick  = { selected = Screen.PERMISSIONS },
                    icon     = { Icon(Icons.Default.Security, null) },
                    label    = { Text("Permisos", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selected == Screen.DETECTOR,
                    onClick  = { selected = Screen.DETECTOR },
                    icon     = { Icon(Icons.Default.BugReport, null) },
                    label    = { Text("Detector", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selected == Screen.GUARD,
                    onClick  = { selected = Screen.GUARD },
                    icon     = { Icon(Icons.Default.Videocam, null) },
                    label    = { Text("Guard", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selected == Screen.ANOMALIES,
                    onClick  = { selected = Screen.ANOMALIES },
                    icon     = { Icon(Icons.Default.GppBad, null) },
                    label    = { Text("Anomalías", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selected == Screen.HUELLA,
                    onClick  = { selected = Screen.HUELLA },
                    icon     = { Icon(Icons.Default.Fingerprint, null) },
                    label    = { Text("Huella", fontSize = 10.sp) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                Screen.PERMISSIONS -> PermissionsScreen()
                Screen.DETECTOR    -> DetectorScreen()
                Screen.GUARD       -> GuardScreen()
                Screen.ANOMALIES   -> AnomaliesScreen()
                Screen.HUELLA      -> HuellaScreen()
            }
        }
    }
}
