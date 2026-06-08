package com.enmanuelgil.androidsecurity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.androidsecurity.data.PermissionAnalyzer
import com.enmanuelgil.androidsecurity.model.SensorAccessEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GuardViewModel : ViewModel() {

    private val _accesses = MutableStateFlow<List<SensorAccessEntry>>(emptyList())
    val accesses: StateFlow<List<SensorAccessEntry>> = _accesses

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun load(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                _accesses.value = PermissionAnalyzer.getCamMicAccesses(context)
            } catch (e: Exception) {
                _accesses.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}
