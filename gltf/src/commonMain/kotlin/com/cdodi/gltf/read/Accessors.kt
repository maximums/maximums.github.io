package com.cdodi.gltf.read

import com.cdodi.gltf.GltfException
import com.cdodi.gltf.glb.float32
import com.cdodi.gltf.glb.int16
import com.cdodi.gltf.glb.int32
import com.cdodi.gltf.glb.uint16
import com.cdodi.gltf.schema.Accessor
import com.cdodi.gltf.schema.Accessor.ComponentType
import com.cdodi.gltf.schema.Document
import kotlin.math.max

/**
 * Reads accessors (the spec's "Accessors"): an accessor is [Accessor.count] elements of a type (`VEC3`…) laid out in a
 * buffer view, from an offset, a stride apart. Everything is checked against the binary chunk before a byte is read, so
 * a broken file is an error naming the accessor, never a read past the end.
 */
internal class Accessors(private val document: Document, private val bin: ByteArray?) {

    /**
     * Accessor [index] as floats, its components one after another. Integer components are converted the spec's way:
     * normalized ones to 0..1 (unsigned) or −1..1 (signed), others as their value (`KHR_mesh_quantization`'s positions).
     * [expectedType] guards against an accessor of the wrong shape for its use, [use] names that use in errors.
     */
    fun floats(index: Int, expectedType: String, use: String): FloatArray {
        val accessor = accessor(index, use)
        if (accessor.type != expectedType) throw GltfException("Accessor $index ($use) is ${accessor.type}, expected $expectedType")
        val components = componentsOf(accessor, index)
        val out = FloatArray(accessor.count * components)
        val layout = layout(accessor, index, components) ?: return out // no buffer view: all zeros, as the spec says
        val bytes = bin!!
        val read: (Int) -> Float = when (accessor.componentType) {
            ComponentType.FLOAT -> { at -> bytes.float32(at) }
            ComponentType.BYTE -> if (accessor.normalized) { at -> max(bytes[at] / 127f, -1f) } else { at -> bytes[at].toFloat() }
            ComponentType.UNSIGNED_BYTE -> if (accessor.normalized) { at -> (bytes[at].toInt() and 0xFF) / 255f } else { at -> (bytes[at].toInt() and 0xFF).toFloat() }
            ComponentType.SHORT -> if (accessor.normalized) { at -> max(bytes.int16(at) / 32767f, -1f) } else { at -> bytes.int16(at).toFloat() }
            ComponentType.UNSIGNED_SHORT -> if (accessor.normalized) { at -> bytes.uint16(at) / 65535f } else { at -> bytes.uint16(at).toFloat() }
            ComponentType.UNSIGNED_INT -> { at -> (bytes.int32(at).toLong() and 0xFFFFFFFFL).toFloat() }
            else -> throw GltfException("Accessor $index ($use) has an unknown component type ${accessor.componentType}")
        }
        var o = 0
        for (i in 0 until accessor.count) {
            val element = layout.start + i * layout.stride
            for (c in 0 until components) out[o++] = read(element + c * layout.componentSize)
        }
        return out
    }

    /** Accessor [index] as triangle indices, each checked to name one of [vertexCount] vertices. */
    fun indices(index: Int, vertexCount: Int, use: String): IntArray {
        val accessor = accessor(index, use)
        if (accessor.type != "SCALAR") throw GltfException("Accessor $index ($use) is ${accessor.type}; indices are SCALAR")
        val read: (ByteArray, Int) -> Int = when (accessor.componentType) {
            ComponentType.UNSIGNED_BYTE -> { b, at -> b[at].toInt() and 0xFF }
            ComponentType.UNSIGNED_SHORT -> { b, at -> b.uint16(at) }
            ComponentType.UNSIGNED_INT -> { b, at -> b.int32(at) }
            else -> throw GltfException("Accessor $index ($use) has component type ${accessor.componentType}; indices are unsigned bytes, shorts or ints")
        }
        val layout = layout(accessor, index, 1) ?: return IntArray(accessor.count)
        val out = IntArray(accessor.count) { i -> read(bin!!, layout.start + i * layout.stride) }
        for (value in out) {
            if (value !in 0 until vertexCount) throw GltfException("Accessor $index ($use) names vertex ${value.toUInt()}, of $vertexCount")
        }
        return out
    }

    /** How many elements accessor [index] has. */
    fun count(index: Int, use: String): Int = accessor(index, use).count

    /** The [Accessor.min] and [Accessor.max] it declares, when they're three values each (as `POSITION` must have). */
    fun declaredRange(index: Int): Pair<List<Float>, List<Float>>? {
        val accessor = document.accessors.getOrNull(index) ?: return null
        val min = accessor.min ?: return null
        val max = accessor.max ?: return null
        return if (min.size == 3 && max.size == 3) min to max else null
    }

    private fun accessor(index: Int, use: String): Accessor {
        val accessor = document.accessors.getOrNull(index) ?: throw GltfException("Accessor $index ($use) doesn't exist")
        if (accessor.sparse != null) throw GltfException("Accessor $index ($use) is sparse; sparse accessors aren't read")
        if (accessor.count < 0) throw GltfException("Accessor $index ($use) has a negative count")
        return accessor
    }

    private fun componentsOf(accessor: Accessor, index: Int): Int = when (accessor.type) {
        "SCALAR" -> 1
        "VEC2" -> 2
        "VEC3" -> 3
        "VEC4" -> 4
        "MAT4" -> 16
        else -> throw GltfException("Accessor $index is ${accessor.type}, which isn't read")
    }

    private class Layout(val start: Int, val stride: Int, val componentSize: Int)

    /** Where accessor [index]'s elements are in the binary chunk, after checking they all fit; null without a buffer view. */
    private fun layout(accessor: Accessor, index: Int, components: Int): Layout? {
        val viewIndex = accessor.bufferView ?: return null
        val view = document.bufferViews.getOrNull(viewIndex) ?: throw GltfException("Accessor $index's buffer view $viewIndex doesn't exist")
        val buffer = document.buffers.getOrNull(view.buffer) ?: throw GltfException("Buffer view $viewIndex's buffer ${view.buffer} doesn't exist")
        if (view.buffer != 0 || buffer.uri != null) throw GltfException("Buffer ${view.buffer} is a separate file; only the .glb's own binary chunk is read")
        val bytes = bin ?: throw GltfException("Accessor $index points into a binary chunk the file doesn't have")
        if (view.byteOffset < 0 || view.byteLength < 0 || view.byteOffset + view.byteLength > bytes.size) {
            throw GltfException("Buffer view $viewIndex (bytes ${view.byteOffset} + ${view.byteLength}) runs past the binary chunk's ${bytes.size}")
        }
        val componentSize = when (accessor.componentType) {
            ComponentType.BYTE, ComponentType.UNSIGNED_BYTE -> 1
            ComponentType.SHORT, ComponentType.UNSIGNED_SHORT -> 2
            ComponentType.UNSIGNED_INT, ComponentType.FLOAT -> 4
            else -> throw GltfException("Accessor $index has an unknown component type ${accessor.componentType}")
        }
        val elementSize = componentSize * components
        val stride = view.byteStride ?: elementSize
        if (stride < elementSize) throw GltfException("Buffer view $viewIndex's stride of $stride is shorter than accessor $index's $elementSize-byte elements")
        val start = view.byteOffset + accessor.byteOffset
        val end = if (accessor.count == 0) start else start + stride * (accessor.count - 1) + elementSize
        if (accessor.byteOffset < 0 || end > view.byteOffset + view.byteLength) {
            throw GltfException("Accessor $index (${accessor.count} × $elementSize bytes, $stride apart) runs past buffer view $viewIndex")
        }
        return Layout(start, stride, componentSize)
    }
}
