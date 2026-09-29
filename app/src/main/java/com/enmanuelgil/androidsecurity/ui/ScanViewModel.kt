package com.enmanuelgil.androidsecurity.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.androidsecurity.data.AppEntry
import com.enmanuelgil.androidsecurity.data.AppScanner
import com.enmanuelgil.androidsecurity.data.Check
import com.enmanuelgil.androidsecurity.data.DeviceChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ScanState(
    val loading     : Boolean = true,
    val apps        : List<AppEntry> = emptyList(),
    val checks      : List<Check> = emptyList(),
    val usageAccess : Boolean = false,
    val error       : String? = null,
    val scannedAt   : Long = 0L,
)

/** Un único análisis compartido por todas las pestañas; se repite al volver a la app (p. ej. tras quitar un permiso). */
class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state
    private var job: Job? = null

    init { refresh() }

    fun refresh(force: Boolean = true) {
        if (job?.isActive == true) return
        if (!force && System.currentTimeMillis() - _state.value.scannedAt < 3000) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true, error = null)
            val ctx = getApplication<Application>()
            _state.value = try {
                val apps = AppScanner.scan(ctx)
                ScanState(false, apps, DeviceChecks.run(ctx, apps), AppScanner.hasUsageAccess(ctx), null, System.currentTimeMillis())
            } catch (e: Exception) {
                _state.value.copy(loading = false, error = "No se pudo analizar el móvil: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
