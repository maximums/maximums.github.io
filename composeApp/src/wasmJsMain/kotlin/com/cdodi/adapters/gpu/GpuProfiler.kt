package com.cdodi.adapters.gpu

import com.cdodi.webgpu.bindings.GPUBuffer
import com.cdodi.webgpu.bindings.GPUBufferDescriptor
import com.cdodi.webgpu.bindings.GPUBufferUsage
import com.cdodi.webgpu.bindings.GPUCommandEncoder
import com.cdodi.webgpu.bindings.GPUComputePassTimestampWrites
import com.cdodi.webgpu.bindings.GPUMapMode
import com.cdodi.webgpu.bindings.GPUQuerySet
import com.cdodi.webgpu.bindings.GPUQuerySetDescriptor
import com.cdodi.webgpu.bindings.GPUQueryType
import com.cdodi.webgpu.bindings.GPURenderPassTimestampWrites
import com.cdodi.webgpu.bindings.mapAsyncSuspend
import com.cdodi.webgpu.bindings.timestamp
import com.cdodi.webgpu.context.GpuContext
import com.cdodi.webgpu.resource.resourceScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.get

/** How long one named span of GPU work took, smoothed over recent frames. */
data class PassTiming(val name: String, val milliseconds: Double)

/**
 * Measures GPU passes with `timestamp-query` (needs the device feature). Each frame, every pass that wants measuring
 * asks for a named [Span]: GPU timestamps are written at its start and end. A span can cover several passes, starting
 * in the first and ending in the last. After the frame, the timestamps are resolved into one of a few rotating
 * `MAP_READ` buffers and read a frame or two later, so profiling never makes the GPU wait.
 *
 * Results go to [timings], smoothed so the numbers are readable. Browsers coarsen timestamps (to 100 µs in Chrome by
 * default), so very short passes read as 0 or jump between steps; the smoothing evens that out.
 */
class GpuProfiler(private val gpu: GpuContext, private val scope: CoroutineScope, private val timings: MutableStateFlow<List<PassTiming>?>) : AutoCloseable {

    /** One measured span: write [compute] or [render] into the pass descriptors of the passes it covers. */
    class Span internal constructor(private val querySet: GPUQuerySet, private val begin: Int, private val end: Int) {
        /** For a compute pass; [first] and [last] say whether this pass starts or ends the span. */
        fun compute(first: Boolean = true, last: Boolean = true) =
            GPUComputePassTimestampWrites(querySet, beginningOfPassWriteIndex = begin.takeIf { first }, endOfPassWriteIndex = end.takeIf { last })

        fun render(first: Boolean = true, last: Boolean = true) =
            GPURenderPassTimestampWrites(querySet, beginningOfPassWriteIndex = begin.takeIf { first }, endOfPassWriteIndex = end.takeIf { last })
    }

    private val resources = gpu.resourceScope()
    private val querySet = gpu.device.createQuerySet(GPUQuerySetDescriptor(type = GPUQueryType.timestamp, count = MAX_SPANS * 2, label = "profiler"))
    private val resolved = resources.buffer(
        GPUBufferDescriptor(size = MAX_SPANS * 16.0, usage = GPUBufferUsage.QUERY_RESOLVE or GPUBufferUsage.COPY_SRC, label = "profiler resolved")
    )
    private val readbacks = List(3) { Readback(resources.buffer(GPUBufferDescriptor(size = MAX_SPANS * 16.0, usage = GPUBufferUsage.MAP_READ or GPUBufferUsage.COPY_DST, label = "profiler readback $it"))) }
    private val names = mutableListOf<String>()
    private var pending: Readback? = null
    private val smoothed = LinkedHashMap<String, Double>()
    private val lastMeasured = mutableMapOf<String, Int>()
    private var readbacksDone = 0

    init {
        timings.value = emptyList()
    }

    /** Starts a frame's measurements. */
    fun beginFrame() = names.clear()

    /** A new span for this frame, or null if the frame already has as many as can be measured. */
    fun span(name: String): Span? {
        if (names.size >= MAX_SPANS) return null
        val index = names.size
        names += name
        return Span(querySet, index * 2, index * 2 + 1)
    }

    /** Records copying this frame's timestamps out; call before finishing the encoder. */
    fun resolve(encoder: GPUCommandEncoder) {
        if (names.isEmpty()) return
        val readback = readbacks.firstOrNull { !it.isBusy } ?: return // all still on their way back: skip this frame
        val queries = names.size * 2
        encoder.resolveQuerySet(querySet, 0, queries, resolved, 0.0)
        encoder.copyBufferToBuffer(resolved, 0.0, readback.buffer, 0.0, queries * 8.0)
        readback.isBusy = true
        readback.names = names.toList()
        pending = readback
    }

    /** Starts reading the timestamps resolved this frame. */
    fun afterSubmit() {
        val readback = pending ?: return
        pending = null
        scope.launch {
            readback.buffer.mapAsyncSuspend(GPUMapMode.READ)
            val words = Uint32Array(readback.buffer.getMappedRange().unsafeCast<ArrayBuffer>())
            fun timestamp(query: Int): Double = (words[query * 2 + 1].toLong() and 0xFFFFFFFFL) * 4_294_967_296.0 + (words[query * 2].toLong() and 0xFFFFFFFFL)
            readbacksDone++
            readback.names.forEachIndexed { index, name ->
                val milliseconds = ((timestamp(index * 2 + 1) - timestamp(index * 2)) / 1e6).coerceAtLeast(0.0)
                smoothed[name] = smoothed[name]?.let { it + (milliseconds - it) * SMOOTHING } ?: milliseconds
                lastMeasured[name] = readbacksDone
            }
            readback.buffer.unmap()
            readback.isBusy = false
            // A pass that doesn't run every frame (Life steps 12 times a second) stays listed; one that stopped (a paused
            // simulation) drops out after a couple of seconds.
            timings.value = smoothed.filterKeys { readbacksDone - lastMeasured.getValue(it) <= KEEP_FOR }.map { (name, ms) -> PassTiming(name, ms) }
        }
    }

    override fun close() {
        querySet.destroy()
        resources.close()
    }

    private class Readback(val buffer: GPUBuffer) {
        var isBusy = false
        var names: List<String> = emptyList()
    }

    companion object {
        const val FEATURE = "timestamp-query"
        private const val MAX_SPANS = 16
        private const val SMOOTHING = 0.08
        private const val KEEP_FOR = 120 // readbacks, about two seconds
    }
}
