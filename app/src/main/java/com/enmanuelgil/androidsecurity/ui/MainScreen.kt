package com.enmanuelgil.androidsecurity.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.enmanuelgil.androidsecurity.R
import com.enmanuelgil.androidsecurity.ui.screens.*

enum class Screen { PERMISSIONS, DETECTOR, GUARD, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selected by remember { mutableStateOf(Screen.PERMISSIONS) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor  = MaterialTheme.colorScheme.surface,
                tonalElevation  = 0.dp
            ) {
                NavigationBarItem(
                    selected = selected == Screen.PERMISSIONS,
                    onClick  = { selected = Screen.PERMISSIONS },
                    icon     = { Icon(Icons.Default.Security, contentDescription = null) },
                    label    = { Text(stringResource(R.string.tab_permissions)) }
                )
                NavigationBarItem(
                    selected = selected == Screen.DETECTOR,
                    onClick  = { selected = Screen.DETECTOR },
                    icon     = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    label    = { Text(stringResource(R.string.tab_detector)) }
                )
                NavigationBarItem(
                    selected = selected == Screen.GUARD,
                    onClick  = { selected = Screen.GUARD },
                    icon     = { Icon(Icons.Default.Videocam, contentDescription = null) },
                    label    = { Text(stringResource(R.string.tab_guard)) }
                )
                NavigationBarItem(
                    selected = selected == Screen.HISTORY,
                    onClick  = { selected = Screen.HISTORY },
                    icon     = { Icon(Icons.Default.History, contentDescription = null) },
                    label    = { Text(stringResource(R.string.tab_history)) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                Screen.PERMISSIONS -> PermissionsScreen()
                Screen.DETECTOR    -> DetectorScreen()
                Screen.GUARD       -> GuardScreen()
                Screen.HISTORY     -> HistoryScreen()
            }
        }
    }
}
