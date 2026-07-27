package com.cdodi.buses

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface TimeBus {
    val ticks: SharedFlow<Float>
}

class TimeBusImpl : TimeBus {
    private val _ticks = MutableSharedFlow<Float>(extraBufferCapacity = 1)
    override val ticks: SharedFlow<Float> = _ticks.asSharedFlow()

    fun onFrame(deltaTime: Float) {
        _ticks.tryEmit(deltaTime)
    }
}

val LocalTimeBus = staticCompositionLocalOf<TimeBus> {
    error("No TimeBus provided!!!")
}
