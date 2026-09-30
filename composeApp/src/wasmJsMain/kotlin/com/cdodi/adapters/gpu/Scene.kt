package com.cdodi.adapters.gpu

import com.cdodi.adapters.input.InputState
import com.cdodi.core.navigation.graph.Route
import com.cdodi.core.time.Clock
import com.cdodi.core.time.Frame
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.context.GpuContext

/**
 * What the WebGPU layer draws for a destination: the background, the Life grid, the flock. The [SceneHost] prepares
 * a scene before it is first shown, steps it on its own [clock] while it is visible, and encodes it into whatever the
 * frame needs: the canvas, or an offscreen texture that a transition blends.
 */
interface Scene : AutoCloseable {

    /** The clock the scene runs on; pausing it pauses the scene. */
    val clock: Clock

    /** Compiles pipelines and allocates buffers. When it returns, the host raises `sceneReady` for the scene's routes. */
    suspend fun prepare(gpu: GpuContext, format: GPUTextureFormat)

    /** The target size changed (device pixels). Called before the first [encode] and after every resize. */
    fun resize(width: Int, height: Int)

    /** Steps the scene in its clock's Simulation phase, with this frame's input. */
    fun update(frame: Frame, input: InputState)

    /** Records drawing into [target]: the canvas, or an offscreen texture of the same format and size. */
    fun encode(encoder: GPUCommandEncoder, target: GPUTextureView)
}

/** Which scene each route shows. Routes may share one scene (every page shows the background, for now). */
class SceneRegistry(private val scenes: Map<Route<*>, Scene>) {

    fun sceneFor(route: Route<*>): Scene? = scenes[route]

    fun routesOf(scene: Scene): List<Route<*>> = scenes.filterValues { it === scene }.keys.toList()

    val all: Set<Scene> get() = scenes.values.toSet()
}
