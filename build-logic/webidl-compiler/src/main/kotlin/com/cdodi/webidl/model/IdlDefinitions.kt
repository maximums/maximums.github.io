package com.cdodi.webidl.model

/**
 * Immutable snapshot of every definition the frontend collected. Each semantic pass takes one and returns a new one;
 * nothing is ever changed in place. Maps keep the order definitions appeared in, which is also the output order.
 */
data class IdlDefinitions(
    val interfaces: Map<String, Descriptor.InterfaceDescriptor>,
    val partialInterfaces: Map<String, Descriptor.InterfaceDescriptor>,
    val mixins: Map<String, Descriptor.InterfaceDescriptor>,
    val includes: List<IncludesDirective>,
    val dictionaries: Map<String, Descriptor.InterfaceDescriptor>,
    val partialDictionaries: Map<String, Descriptor.InterfaceDescriptor>,
    val enums: Map<String, Descriptor.EnumDescriptor>,
    val typedefs: Map<String, Descriptor.TypeDescriptor>,
    val namespaces: Map<String, Descriptor.InterfaceDescriptor>,
) {
    fun toResolvedContext() = ResolvedBindingContext(
        interfaces = interfaces,
        dictionaries = dictionaries,
        enums = enums,
        namespaces = namespaces,
    )

    companion object {
        fun from(collected: BindingContext) = IdlDefinitions(
            interfaces = collected[BindingSlices.INTERFACE].orEmpty().toMap(),
            partialInterfaces = collected[BindingSlices.PARTIAL_INTERFACE].orEmpty().toMap(),
            mixins = collected[BindingSlices.MIXIN].orEmpty().toMap(),
            includes = collected[BindingSlices.INCLUDES].orEmpty().values.toList(),
            dictionaries = collected[BindingSlices.DICTIONARY].orEmpty().toMap(),
            partialDictionaries = collected[BindingSlices.PARTIAL_DICTIONARY].orEmpty().toMap(),
            enums = collected[BindingSlices.ENUM].orEmpty().toMap(),
            typedefs = collected[BindingSlices.TYPEDEF].orEmpty().toMap(),
            namespaces = collected[BindingSlices.NAMESPACE].orEmpty().toMap(),
        )
    }
}
