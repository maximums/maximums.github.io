package com.cdodi.buses

import androidx.compose.runtime.staticCompositionLocalOf
import com.cdodi.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface AppEventBus {
    val events: Flow<AppEvent>

    fun sendEvent(event: AppEvent)  // TODO for now, will think about it later
}

class AppEventBusImpl(private val scope: CoroutineScope) : AppEventBus {
    private val _events = Channel<AppEvent>(Channel.BUFFERED)
    override val events = _events.receiveAsFlow()

    override fun sendEvent(event: AppEvent) {
        scope.launch {
            _events.send(event)
        }
    }
}

val LocalAppEventBus = staticCompositionLocalOf<AppEventBus> {
    error("No AppEventBus provided!")
}

sealed interface AppEvent {
    value class NavigateTo(val page: Page) : AppEvent
    value class ShowNotification(val message: String) : AppEvent
    data object ToggleTheme : AppEvent // TODO make it also a value class which will wrap a Theme ID, but first themes should be implemented
}
