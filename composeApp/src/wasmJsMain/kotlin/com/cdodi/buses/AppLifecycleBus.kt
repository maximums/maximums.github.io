package com.cdodi.buses

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface AppLifecycleBus {
    val appState: StateFlow<AppLifeCycle>
}

class AppLifecycleBusImp(private val scope: CoroutineScope) : AppLifecycleBus {
    private val _state = MutableStateFlow(AppLifeCycle.INITIALIZING)
    override val appState: StateFlow<AppLifeCycle> = _state.asStateFlow()

    fun updateTo(state: AppLifeCycle) {
        _state.update { state }
    }
}

enum class AppLifeCycle {
    INITIALIZING,
    FOREGROUND,
    BACKGROUND,
}
