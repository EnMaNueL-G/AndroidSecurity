package com.enmanuelgil.androidsecurity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.androidsecurity.data.AccessEvent
import com.enmanuelgil.androidsecurity.data.AccessLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {

    private val _events  = MutableStateFlow<List<AccessEvent>>(emptyList())
    val events: StateFlow<List<AccessEvent>> = _events

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun load(context: Context, windowMs: Long = 24L * 60 * 60 * 1000L) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            _events.value  = AccessLog.readInWindow(context, windowMs)
            _loading.value = false
        }
    }
}
