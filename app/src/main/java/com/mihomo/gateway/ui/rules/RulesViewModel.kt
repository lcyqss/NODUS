package com.mihomo.gateway.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.RuleInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RulesViewModel(
    private val api: MihomoApi = AppContainer.api
) : ViewModel() {

    private val _rules = MutableStateFlow<List<RuleInfo>>(emptyList())
    val rules: StateFlow<List<RuleInfo>> = _rules.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loaded = false

    fun loadIfNeeded() {
        if (loaded) return
        load()
    }

    fun load() {
        viewModelScope.launch {
            api.rules()
                .onSuccess {
                    _rules.value = it
                    _error.value = null
                    loaded = true
                }
                .onFailure { _error.value = it.message }
        }
    }
}
