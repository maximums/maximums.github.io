package com.cdodi.adapters.gpu

import com.cdodi.webgpu.bindings.GPUCanvasAlphaMode
import com.cdodi.webgpu.bindings.GPUCanvasConfiguration
import com.cdodi.webgpu.bindings.GPUCanvasContext
import com.cdodi.webgpu.bindings.GPUTextureFormat
import com.cdodi.webgpu.bindings.GPUTextureView
import com.cdodi.webgpu.bindings.opaque
import com.cdodi.webgpu.context.GpuContext
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.roundToInt

/**
 * The WebGPU canvas under the Compose layer, configured opaque. Its drawing buffer follows its CSS box in *device*
 * pixels, so the scene stays sharp at any zoom or DPR: a `ResizeObserver` reports the exact device-pixel size, and
 * the change is applied at the start of the next Render phase rather than in the middle of a frame.
 */
class GpuCanvas(private val element: HTMLCanvasElement, private val gpu: GpuContext) : AutoCloseable {

    val format: GPUTextureFormat = gpu.preferredFormat

    private val context: GPUCanvasContext = webGpuContextOf(element)
    private var pendingWidth = (element.clientWidth * window.devicePixelRatio).roundToInt()
    private var pendingHeight = (element.clientHeight * window.devicePixelRatio).roundToInt()
    private var hasPendingSize = true
    private var lastDevicePixelRatio = window.devicePixelRatio
    private val observer = observeDevicePixelSize(element) { width, height ->
        pendingWidth = width
        pendingHeight = height
        hasPendingSize = true
    }

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    init {
        context.configure(GPUCanvasConfiguration(device = gpu.device, format = format, alphaMode = GPUCanvasAlphaMode.opaque))
    }

    /** Applies the size reported since the last frame. True if it changed. */
    fun applyPendingSize(): Boolean {
        // A DPR change alone (another screen) doesn't always reach the observer; a number read per frame is cheap.
        if (window.devicePixelRatio != lastDevicePixelRatio) {
            lastDevicePixelRatio = window.devicePixelRatio
            remeasure(observer)
        }
        if (!hasPendingSize) return false
        hasPendingSize = false
        val max = gpu.device.limits.maxTextureDimension2D
        val newWidth = pendingWidth.coerceIn(1, max)
        val newHeight = pendingHeight.coerceIn(1, max)
        if (newWidth == width && newHeight == height) return false
        width = newWidth
        height = newHeight
        element.width = newWidth
        element.height = newHeight
        return true
    }

    /** This frame's canvas texture. */
    fun currentView(): GPUTextureView = context.getCurrentTexture().createView()

    override fun close() {
        disconnect(observer)
        context.unconfigure()
    }
}

/**
 * Reports [element]'s size in device pixels now and on every change. Returns a handle for [remeasure] and [disconnect].
 *
 * `device-pixel-content-box` is exact, so it wins while it agrees with CSS size × DPR. Where it doesn't, CSS × DPR
 * does: DevTools' device mode, for one, emulates the DPR (the page renders at 2×) but reports the device box in CSS
 * pixels, which would leave the scene upscaled and blurry.
 */
private fun observeDevicePixelSize(element: HTMLCanvasElement, onResize: (Int, Int) -> Unit): JsAny = js(
    """{
    let last = null;
    const report = (entry) => {
        last = entry || last;
        if (!last) return;
        const dpr = devicePixelRatio;
        const cssWidth = Math.round(last.contentRect.width * dpr);
        const cssHeight = Math.round(last.contentRect.height * dpr);
        const box = last.devicePixelContentBoxSize && last.devicePixelContentBoxSize[0];
        const exact = box && Math.abs(box.inlineSize - cssWidth) <= 1 && Math.abs(box.blockSize - cssHeight) <= 1;
        onResize(exact ? box.inlineSize : cssWidth, exact ? box.blockSize : cssHeight);
    };
    const observer = new ResizeObserver((entries) => report(entries[entries.length - 1]));
    try {
        observer.observe(element, { box: 'device-pixel-content-box' });
    } catch (e) {
        observer.observe(element);
    }
    return { observer, remeasure: () => report(null) };
}"""
)

/** Reports the last observed size again, measured with the current DPR. */
private fun remeasure(handle: JsAny): Unit = js("handle.remeasure()")

private fun disconnect(handle: JsAny): Unit = js("handle.observer.disconnect()")

// kotlinx-browser types getContext's result as a RenderingContext, which isn't a JsAny to cast from.
private fun webGpuContextOf(canvas: HTMLCanvasElement): GPUCanvasContext = js("canvas.getContext('webgpu')")
