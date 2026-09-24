package com.cdodi.webgpu.resource

import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUDevice
import com.cdodi.webgpu.bindings.GPUTexture
import com.cdodi.webgpu.bindings.GPUTextureDescriptor
import com.cdodi.webgpu.context.GpuContext

/**
 * Owns GPU resources and destroys them together, in reverse order of creation. Each scene holds one and closes it on
 * dispose, so GPU memory is released right away instead of whenever the JS garbage collector gets to it.
 *
 * The raw WebGPU objects are returned, so the whole generated API stays usable on them.
 */
class ResourceScope(private val device: GPUDevice) : AutoCloseable {
    private val cleanups = ArrayDeque<() -> Unit>()
    var isClosed = false
        private set

    fun buffer(descriptor: GPUBufferDescriptor): GPUBuffer = device.createBuffer(descriptor).also { own(it::destroy) }

    fun texture(descriptor: GPUTextureDescriptor): GPUTexture = device.createTexture(descriptor).also { own(it::destroy) }

    /** Registers any other cleanup (a nested scope, an event listener, ...), run when this scope closes. */
    fun own(cleanup: () -> Unit) {
        check(!isClosed) { "ResourceScope is already closed" }
        cleanups.addFirst(cleanup)
    }

    /** Runs every cleanup, even if one fails; the first failure is rethrown afterwards. */
    override fun close() {
        if (isClosed) return
        isClosed = true

        var failure: Throwable? = null
        while (cleanups.isNotEmpty()) {
            try {
                cleanups.removeFirst().invoke()
            } catch (e: Throwable) {
                failure = failure ?: e
            }
        }
        failure?.let { throw it }
    }
}

fun GpuContext.resourceScope() = ResourceScope(device)
