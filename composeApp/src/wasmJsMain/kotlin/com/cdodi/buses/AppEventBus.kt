package com.cdodi.buses

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface AppEventBus {
    val events: Flow<AppEvent>
}

class AppEventBusImpl(private val scope: CoroutineScope) : AppEventBus {
    private val _events = Channel<AppEvent>(Channel.BUFFERED)
    override val events = _events.receiveAsFlow()

    fun sendEvent(event: AppEvent) {
        scope.launch {
            _events.send(event)
        }
    }
}

sealed interface AppEvent