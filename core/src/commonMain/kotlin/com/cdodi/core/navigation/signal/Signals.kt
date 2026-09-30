package com.cdodi.core.navigation.signal

/** A named condition a transition can wait for, e.g. "the Boids scene is ready". */
data class Signal(val name: String) {
    companion object
}

fun interface Signals {
    fun isRaised(signal: Signal): Boolean
}

/** Signals that adapters raise and lower (SceneHost raises `sceneReady` once pipelines are compiled). */
class MutableSignals : Signals {
    private val raised = mutableSetOf<Signal>()

    override fun isRaised(signal: Signal): Boolean = signal in raised

    fun raise(signal: Signal) {
        raised += signal
    }

    fun lower(signal: Signal) {
        raised -= signal
    }
}
