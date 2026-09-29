package com.enmanuelgil.androidsecurity.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.androidsecurity.ui.screens.*

enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Default.Shield),
    APPS("Apps", Icons.Default.Apps),
    ACCESS("Accesos", Icons.Default.AdminPanelSettings),
    GUARD("Guardia", Icons.Default.Videocam),
    INFO("Info", Icons.Default.Info),
}

@Composable
fun MainScreen(initialScreen: Screen = Screen.HOME, vm: ScanViewModel = viewModel()) {
    var selected by rememberSaveable { mutableStateOf(initialScreen) }
    var appsFilter by rememberSaveable { mutableStateOf(AppFilter.REVIEW.name) }
    val state by vm.state.collectAsState()

    // Al volver de Ajustes (p. ej. tras quitar un permiso) se vuelve a analizar
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refresh(force = false) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = selected == s,
                        onClick = { selected = s },
                        icon = { Icon(s.icon, contentDescription = s.label) },
                        label = { Text(s.label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                Screen.HOME -> HomeScreen(state, onRefresh = { vm.refresh() },
                    onOpenApps = { f -> appsFilter = f.name; selected = Screen.APPS })
                Screen.APPS -> AppsScreen(state, AppFilter.valueOf(appsFilter), { appsFilter = it.name }, onRefresh = { vm.refresh() })
                Screen.ACCESS -> AccessScreen(state, onRefresh = { vm.refresh() })
                Screen.GUARD -> GuardScreen()
                Screen.INFO -> InfoScreen()
            }
        }
    }
}
