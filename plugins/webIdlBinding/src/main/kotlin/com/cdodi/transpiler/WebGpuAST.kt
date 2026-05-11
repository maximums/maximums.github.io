package com.cdodi.transpiler

sealed interface Descriptor {
    val name: String

    data class TypeDescriptor(
        override val name: String,
        val isNullable: Boolean = true,
        val unionMembers: List<TypeDescriptor> = emptyList(),
        val record: Map<TypeDescriptor, TypeDescriptor>? = null,
        val sequenceOf: TypeDescriptor? = null,
        val promiseOf: TypeDescriptor? = null
    ) : Descriptor

    data class InterfaceDescriptor(
        override val name: String,
        val members: List<InterfaceMember>,
        val superTypes: Set<String>,
    ) : Descriptor {
        val hasConstructor: Boolean
            get() = members.any { it.name == "constructor" }

        operator fun plus(other: InterfaceDescriptor?): InterfaceDescriptor {
            if (other == null) return this

            return copy(members = members + other.members, superTypes = superTypes + other.superTypes)
        }
    }

    data class EnumDescriptor(
        override val name: String,
        val values: List<String>
    ) : Descriptor
}

sealed interface InterfaceMember : Descriptor {
    data class VariableDescriptor(
        override val name: String,
        val type: Descriptor.TypeDescriptor,
        val isReadonly: Boolean = false,
        val isRequired: Boolean = false,
        val defaultValue: String? = null
    ) : InterfaceMember

    data class FunctionDescriptor(
        override val name: String,
        val returnType: Descriptor.TypeDescriptor,
        val parameters: List<VariableDescriptor>,
    ) : InterfaceMember

    data class ConstantDescriptor(
        override val name: String,
        val type: Descriptor.TypeDescriptor,
        val value: String,
    ) : InterfaceMember
}
