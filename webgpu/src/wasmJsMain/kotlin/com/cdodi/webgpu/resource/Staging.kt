package com.cdodi.webgpu.resource

import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.set

/**
 * A reusable [Float32Array] for uploading Kotlin data (uniforms, small edits) without allocating a new typed array
 * every frame. Kotlin arrays live in the Wasm GC heap, so the copy is element by element; keep bulk data on the GPU.
 */
class Float32Staging(capacity: Int) {
    val array = Float32Array(capacity)

    /** Copies [values] (from index 0) into the start of [array] and returns [array]. */
    fun fill(values: FloatArray, count: Int = values.size): Float32Array {
        require(count <= array.length) { "Staging capacity ${array.length} is smaller than $count values" }
        for (i in 0 until count) array[i] = values[i]
        return array
    }
}

/** Like [Float32Staging], for unsigned 32-bit data (indices, counters, flags). */
class Uint32Staging(capacity: Int) {
    val array = Uint32Array(capacity)

    fun fill(values: IntArray, count: Int = values.size): Uint32Array {
        require(count <= array.length) { "Staging capacity ${array.length} is smaller than $count values" }
        for (i in 0 until count) array[i] = values[i]
        return array
    }
}
