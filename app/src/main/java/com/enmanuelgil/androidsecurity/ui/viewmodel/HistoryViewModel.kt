package com.enmanuelgil.androidsecurity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.androidsecurity.data.PermissionAnalyzer
import com.enmanuelgil.androidsecurity.model.AccessHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {

    private val _history = MutableStateFlow<List<AccessHistoryEntry>>(emptyList())
    val history: StateFlow<List<AccessHistoryEntry>> = _history

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun load(context: Context, windowMs: Long = 24 * 60 * 60 * 1000L) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                _history.value = PermissionAnalyzer.getAccessHistory(context, windowMs)
            } catch (e: Exception) {
                _history.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}
