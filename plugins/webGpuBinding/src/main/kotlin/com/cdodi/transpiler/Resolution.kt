package com.cdodi.transpiler

import kotlin.collections.forEach

fun resolveSemantics(context: MutableBindingContext) {
    val dictionarySlice = context[BindingSlices.PARTIAL_DICTIONARY]?.onEach { (key, value) ->
        val main = context[BindingSlices.DICTIONARY, key] ?: return@onEach

        context[BindingSlices.DICTIONARY, key] = main + value
    }

    dictionarySlice?.let { flattenDictionaries(it) }

    context[BindingSlices.PARTIAL_INTERFACE]?.forEach { (key, value) ->
        val main = context[BindingSlices.INTERFACE, key] ?: return@forEach

        context[BindingSlices.INTERFACE, key] = main + value
    }

    context[BindingSlices.INTERFACE]?.forEach { (key, value) ->
        val externalTypes = value.superTypes.filter { context[BindingSlices.INTERFACE, it] == null }
        val newTypes = value.superTypes - externalTypes.toSet() + "JsAny"
        context[BindingSlices.INTERFACE, key] = value.copy(superTypes = newTypes)
    }

    resolveTypesInContext(context)
}

private fun flattenDictionaries(dictionaries: MutableMap<String, Descriptor.InterfaceDescriptor>) {
    fun Map<String, Descriptor.InterfaceDescriptor>.collectDictionaryMembers(name: String): List<InterfaceMember> {
        val dict = this[name] ?: return emptyList()
        return dict.superTypes.fold(dict.members) { acc, father ->
            acc + collectDictionaryMembers(father)
        }
    }

    val flattenedUpdates = dictionaries.mapNotNull { (name, descriptor) ->
        if (descriptor.superTypes.isEmpty()) return@mapNotNull null

        val allMembers = dictionaries.collectDictionaryMembers(name)
        name to descriptor.copy(members = allMembers, superTypes = emptySet())
    }.toMap()

    dictionaries.putAll(flattenedUpdates)
}

fun Descriptor.TypeDescriptor.unrollTypedefs(context: BindingContext): Descriptor.TypeDescriptor {
    val typedef = context[BindingSlices.TYPEDEF, name]
    if (typedef != null) return typedef.unrollTypedefs(context).copy(isNullable = isNullable || typedef.isNullable)

    return copy(
        unionMembers = unionMembers.map { it.unrollTypedefs(context) },
        sequenceOf = sequenceOf?.unrollTypedefs(context),
        promiseOf = promiseOf?.unrollTypedefs(context),
        record = record?.entries?.associate {
            it.key.unrollTypedefs(context) to it.value.unrollTypedefs(context)
        }
    )
}

fun Descriptor.TypeDescriptor.resolveUnions(context: MutableBindingContext): Descriptor.TypeDescriptor {
    val resolvedSequence = sequenceOf?.resolveUnions(context)
    val resolvedPromise = promiseOf?.resolveUnions(context)
    val resolvedRecord = record?.entries?.associate {
        it.key.resolveUnions(context) to it.value.resolveUnions(context)
    }

    if (unionMembers.isEmpty()) return copy(sequenceOf = resolvedSequence, promiseOf = resolvedPromise, record = resolvedRecord)

    val members = unionMembers.map { it.resolveUnions(context) }
    val isAllCustomObjects = members.all {
        context[BindingSlices.INTERFACE, it.name] != null || context[BindingSlices.DICTIONARY, it.name] != null
    }

    // TODO Extract
    if (isAllCustomObjects) {
        val markerInterfaceName = members.joinToString(separator = "Or") { it.name }

        if (context[BindingSlices.INTERFACE, markerInterfaceName] == null) {
            context[BindingSlices.INTERFACE, markerInterfaceName] = Descriptor.InterfaceDescriptor(
                name = markerInterfaceName,
                members = emptyList(),
                superTypes = setOf("JsAny")
            )
        }

        members.forEach { member ->
            context[BindingSlices.INTERFACE, member.name]?.let {
                context[BindingSlices.INTERFACE, member.name] =
                    it.copy(superTypes = it.superTypes - "JsAny" + markerInterfaceName)
            }
            context[BindingSlices.DICTIONARY, member.name]?.let {
                context[BindingSlices.DICTIONARY, member.name] =
                    it.copy(superTypes = it.superTypes - "JsAny" + markerInterfaceName)
            }
        }

        return Descriptor.TypeDescriptor(name = markerInterfaceName, isNullable = isNullable)
    } else {
        return Descriptor.TypeDescriptor(name = "any", isNullable = isNullable)
    }
}

private fun resolveTypesInContext(context: MutableBindingContext) {
    fun resolveMember(member: InterfaceMember): InterfaceMember {
        return when (member) {
            is InterfaceMember.VariableDescriptor -> {
                val newType = member.type.unrollTypedefs(context).resolveUnions(context)
                member.copy(type = newType)
            }

            is InterfaceMember.FunctionDescriptor -> {
                val newReturn = member.returnType.unrollTypedefs(context).resolveUnions(context)
                val newParams = member.parameters.map { param ->
//                    val newParamType = param.type.unrollTypedefs(context).resolveUnions(context)
                    val newParamType = param.type.unrollTypedefs(context)
                    param.copy(type = newParamType)
                }
                member.copy(returnType = newReturn, parameters = newParams)
            }
        }
    }

    // Process all Interfaces
    val interfaces = context[BindingSlices.INTERFACE] ?: emptyMap()
    interfaces.forEach { (name, descriptor) ->
        val resolvedMembers = descriptor.members.map { resolveMember(it) }
        context[BindingSlices.INTERFACE, name] = descriptor.copy(members = resolvedMembers)
    }

    // Process all Dictionaries
    val dictionaries = context[BindingSlices.DICTIONARY] ?: emptyMap()
    dictionaries.forEach { (name, descriptor) ->
        val resolvedMembers = descriptor.members.map { resolveMember(it) }
        context[BindingSlices.DICTIONARY, name] = descriptor.copy(members = resolvedMembers)
    }
}
