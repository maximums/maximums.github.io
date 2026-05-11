package com.cdodi.transpiler

private const val SLICE_DELIMITER = "\n---------------------------------------------------------------------------------------------------------------------\n"

interface BindingContext {
    operator fun <V: Any>get(slice: Slice<String, V>, key: String): V?
    operator fun <V: Any>get(slice: Slice<String, V>): Map<String, V>?
}

class MutableBindingContext : BindingContext {
    private val storage = mutableMapOf<Slice<*, *>, MutableMap<String, Any>>()

    operator fun <V : Any> set(slice: Slice<String, V>, key: String, value: V) {
        storage.getOrPut(slice) { mutableMapOf() }[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V : Any> get(slice: Slice<String, V>): MutableMap<String, V>? =
        storage[slice] as MutableMap<String, V>?

    @Suppress("UNCHECKED_CAST")
    operator fun <V : Any> set(slice: Slice<String, V>, value: Map<String, V>) =
        storage.set(slice, value as MutableMap<String, Any>)

    @Suppress("UNCHECKED_CAST")
    override fun <V: Any> get(slice: Slice<String, V>, key: String): V? = storage[slice]?.get(key) as? V

    override fun toString(): String = storage.entries.joinToString(separator = SLICE_DELIMITER) { entry ->
        "${entry.key.name} -- ${entry.value.entries.joinToString(separator = "\n")}"
    }
}

/**
 * Immutable context returned by [resolveSemantics]. Only exposes the final merged slices —
 * partials, mixins, and typedefs have already been folded in and are not accessible.
 */
class ResolvedBindingContext(
    private val interfaces: Map<String, Descriptor.InterfaceDescriptor>,
    private val dictionaries: Map<String, Descriptor.InterfaceDescriptor>,
    private val enums: Map<String, Descriptor.EnumDescriptor>,
) : BindingContext {

    @Suppress("UNCHECKED_CAST")
    override fun <V : Any> get(slice: Slice<String, V>): Map<String, V>? = when (slice) {
        BindingSlices.INTERFACE -> interfaces as Map<String, V>
        BindingSlices.DICTIONARY -> dictionaries as Map<String, V>
        BindingSlices.ENUM -> enums as Map<String, V>
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V : Any> get(slice: Slice<String, V>, key: String): V? = get(slice)?.get(key)
}

data class Slice<K, V>(val name: String)

data class IncludesDirective(val targetName: String, val mixinName: String)

object BindingSlices {
    // --- 1. Interfaces ---
    val INTERFACE = Slice<String, Descriptor.InterfaceDescriptor>("INTERFACE")
    val PARTIAL_INTERFACE = Slice<String, Descriptor.InterfaceDescriptor>("PARTIAL_INTERFACE")
    val MIXIN = Slice<String, Descriptor.InterfaceDescriptor>("MIXIN")

    // --- 2. Dictionaries (Reusing InterfaceDescriptor for JS Interop) ---
    val DICTIONARY = Slice<String, Descriptor.InterfaceDescriptor>("DICTIONARY")
    val PARTIAL_DICTIONARY = Slice<String, Descriptor.InterfaceDescriptor>("PARTIAL_DICTIONARY")

    // --- 3. Enums ---
    val ENUM = Slice<String, Descriptor.EnumDescriptor>("ENUM")

    // --- 4. Typedefs (For union unrolling/marker interfaces) ---
    val TYPEDEF = Slice<String, Descriptor.TypeDescriptor>("TYPEDEF")

    // --- 5. Includes directives (resolved in semantic phase) ---
    val INCLUDES = Slice<String, IncludesDirective>("INCLUDES")

    // --- 6. Fallback / Hardcoded Types ---
    val EXTERNAL_TYPE = Slice<String, String>("EXTERNAL_TYPE")
}