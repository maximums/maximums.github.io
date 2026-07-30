package com.cdodi.data.boids

import com.cdodi.buses.TimeBus
import com.cdodi.data.Manager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FlockingManager(bus: TimeBus, dispatcher: CoroutineDispatcher = Dispatchers.Main) : Manager(bus) {
    override val managerScope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("FlockingManager"))

    override fun loop(timeStep: Float) {
        TODO("Not yet implemented")
    }
}