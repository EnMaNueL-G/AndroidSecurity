package com.enmanuelgil.androidsecurity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.androidsecurity.data.AccessEvent
import com.enmanuelgil.androidsecurity.data.AccessLog
import com.enmanuelgil.androidsecurity.data.PermissionAnalyzer
import com.enmanuelgil.androidsecurity.model.SensorAccessEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GuardData(
    val grantedApps   : List<SensorAccessEntry> = emptyList(),
    val serviceEvents : List<AccessEvent>        = emptyList(),
    val loading       : Boolean                  = false
)

class GuardViewModel : ViewModel() {

    private val _data = MutableStateFlow(GuardData(loading = true))
    val data: StateFlow<GuardData> = _data

    fun load(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _data.value = GuardData(loading = true)
            try {
                val apps   = PermissionAnalyzer.getGrantedCamMicApps(context)
                val events = AccessLog.readAll(context)
                _data.value = GuardData(grantedApps = apps, serviceEvents = events, loading = false)
            } catch (e: Exception) {
                _data.value = GuardData(loading = false)
            }
        }
    }

    fun clearHistory(context: Context) {
        AccessLog.clear(context)
        viewModelScope.launch(Dispatchers.IO) {
            val apps = PermissionAnalyzer.getGrantedCamMicApps(context)
            _data.value = GuardData(grantedApps = apps, serviceEvents = emptyList(), loading = false)
        }
    }
}
