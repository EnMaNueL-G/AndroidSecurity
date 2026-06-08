package com.enmanuelgil.androidsecurity.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.androidsecurity.data.PermissionAnalyzer
import com.enmanuelgil.androidsecurity.model.AppPermissionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PermissionsViewModel : ViewModel() {

    private val _apps    = MutableStateFlow<List<AppPermissionInfo>>(emptyList())
    val apps: StateFlow<List<AppPermissionInfo>> = _apps

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun load(context: Context) {
        if (_apps.value.isNotEmpty()) return // already loaded
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                _apps.value = PermissionAnalyzer.getAppPermissions(context)
            } catch (e: Exception) {
                _apps.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}
