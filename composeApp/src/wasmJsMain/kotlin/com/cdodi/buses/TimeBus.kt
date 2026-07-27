package com.cdodi.buses

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

interface TimeBus {
    val ticks: SharedFlow<Float>
}

class TimeBusImpl(private val scope: CoroutineScope) : TimeBus {
    private val _ticks = MutableSharedFlow<Float>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val ticks: SharedFlow<Float> = _ticks.asSharedFlow()

    fun onFrame(deltaTime: Float) {
        scope.launch {
            _ticks.emit(deltaTime)
        }
    }
}

val LocalTimeBus = staticCompositionLocalOf<TimeBus> {
    error("No TimeBus provided!!!")
}
