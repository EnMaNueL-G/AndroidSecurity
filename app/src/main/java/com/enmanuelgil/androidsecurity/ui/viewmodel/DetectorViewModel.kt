package com.enmanuelgil.androidsecurity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.androidsecurity.data.PermissionAnalyzer
import com.enmanuelgil.androidsecurity.model.ThreatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DetectorViewModel : ViewModel() {

    private val _threats = MutableStateFlow<List<ThreatEntry>>(emptyList())
    val threats: StateFlow<List<ThreatEntry>> = _threats

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun load(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                _threats.value = PermissionAnalyzer.getThreats(context)
            } catch (e: Exception) {
                _threats.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}
