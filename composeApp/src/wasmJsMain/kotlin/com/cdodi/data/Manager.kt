package com.cdodi.data

import com.cdodi.buses.TimeBus
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val FIXED_STEP = 0.0166f

abstract class Manager(private val bus: TimeBus) : AutoCloseable {
    private var accumulator = 0f

    protected abstract val managerScope: CoroutineScope

    protected abstract fun loop(timeStep: Float)

    fun start() {
        managerScope.launch {
            bus.ticks.collect { time -> onFrame(visualDeltaTime = time) }
        }
    }

    override fun close() {
        managerScope.cancel(message = "Cleaning '${managerScope.coroutineContext[CoroutineName]?.name ?: "Manager"}'")
    }

    private fun onFrame(visualDeltaTime: Float) {
        accumulator += visualDeltaTime

        while (accumulator >= FIXED_STEP && managerScope.isActive) {
            loop(FIXED_STEP)
            accumulator -= FIXED_STEP
        }
    }
}