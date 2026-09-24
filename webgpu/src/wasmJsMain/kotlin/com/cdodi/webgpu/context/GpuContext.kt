package com.cdodi.webgpu.context

import com.cdodi.webgpu.bindings.GPU
import com.cdodi.webgpu.bindings.GPUAdapter
import com.cdodi.webgpu.bindings.GPUDevice
import com.cdodi.webgpu.bindings.GPUDeviceDescriptor
import com.cdodi.webgpu.bindings.GPUErrorFilter
import com.cdodi.webgpu.bindings.GPUFeatureName
import com.cdodi.webgpu.bindings.GPUPowerPreference
import com.cdodi.webgpu.bindings.GPUQueue
import com.cdodi.webgpu.bindings.GPURequestAdapterOptions
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUUncapturedErrorEvent
import com.cdodi.webgpu.bindings.gpu
import com.cdodi.webgpu.bindings.popErrorScopeSuspend
import com.cdodi.webgpu.bindings.requestAdapterSuspend
import com.cdodi.webgpu.bindings.requestDeviceSuspend
import com.cdodi.webgpu.bindings.validation
import com.cdodi.webgpu.runtime.await
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * Requests an adapter and a device.
 *
 * Returns null when the browser has no WebGPU (`navigator.gpu` is missing) or no suitable adapter: both are normal
 * situations the site handles with a fallback, so they are not exceptions. A device request that fails once an adapter
 * exists is unexpected and throws (JsPromiseRejection with the browser's error).
 *
 * [optionalFeatures] are requested only if the adapter supports them; check [GpuContext.hasFeature] afterwards.
 */
suspend fun requestGpuContext(
    powerPreference: GPUPowerPreference? = null,
    optionalFeatures: Set<String> = emptySet(),
): GpuContext? {
    val gpu = window.navigator.gpu ?: return null
    val adapter = gpu.requestAdapterSuspend(GPURequestAdapterOptions(powerPreference = powerPreference)) ?: return null

    val features = optionalFeatures.filter { adapter.features.has(it) }
    val device = adapter.requestDeviceSuspend(
        GPUDeviceDescriptor(requiredFeatures = features.map { it.toJsString().unsafeCast<GPUFeatureName>() })
    )
    return GpuContext(gpu, adapter, device)
}

/** The site's handle on WebGPU: one adapter, one device, and the things every scene needs from them. */
class GpuContext internal constructor(
    val gpu: GPU,
    val adapter: GPUAdapter,
    val device: GPUDevice,
) : AutoCloseable {

    val queue: GPUQueue get() = device.queue

    /** The texture format canvases should be configured with on this system. */
    val preferredFormat: GPUTextureFormat get() = gpu.getPreferredCanvasFormat()

    fun hasFeature(name: String): Boolean = device.features.has(name)

    /** Suspends until the device is lost (driver reset, `destroy()`, ...). */
    suspend fun awaitLoss(): DeviceLoss {
        val info = device.lost.await()
        return DeviceLoss(reason = info.reason.unsafeCast<JsString>().toString(), message = info.message)
    }

    /**
     * Calls [handler] for every error no error scope captured. Close the result to stop listening.
     * Possible because GPUDevice extends EventTarget in the generated bindings.
     *
     * Errors arrive asynchronously, once the browser has flushed the commands to the GPU process: in the site every
     * frame's submit does that; a test that makes one invalid call must `queue.submit()` (even empty) to see it.
     */
    fun onUncapturedError(handler: (message: String) -> Unit): AutoCloseable {
        val listener: (Event) -> Unit = { event -> handler(event.unsafeCast<GPUUncapturedErrorEvent>().error.message) }
        device.addEventListener("uncapturederror", listener)
        return AutoCloseable { device.removeEventListener("uncapturederror", listener) }
    }

    /**
     * Runs [block] inside an error scope and returns its result together with the first error of type [filter]
     * the GPU reported for it (null when there was none). The scope is always popped, even if [block] throws.
     */
    suspend fun <T> errorScope(filter: GPUErrorFilter = GPUErrorFilter.validation, block: () -> T): Scoped<T> {
        device.pushErrorScope(filter)
        val value = try {
            block()
        } catch (e: Throwable) {
            device.popErrorScope()
            throw e
        }
        return Scoped(value, device.popErrorScopeSuspend()?.message)
    }

    /** Destroys the device; everything created from it becomes invalid. */
    override fun close() = device.destroy()
}

data class Scoped<T>(val value: T, val error: String?)

data class DeviceLoss(val reason: String, val message: String)
