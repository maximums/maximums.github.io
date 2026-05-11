package com.cdodi.transpiler

fun resolveSemantics(context: MutableBindingContext): ResolvedBindingContext {
    resolveMixinIncludes(context)
    mergePartialInterfaces(context)
    mergePartialDictionaries(context)
    flattenDictionaryInheritance(context)
    filterExternalSuperTypes(context)
    resolveTypesInContext(context)

    return ResolvedBindingContext(
        interfaces = context[BindingSlices.INTERFACE]?.toMap().orEmpty(),
        dictionaries = context[BindingSlices.DICTIONARY]?.toMap().orEmpty(),
        enums = context[BindingSlices.ENUM]?.toMap().orEmpty(),
    )
}

private fun resolveMixinIncludes(context: MutableBindingContext) {
    context[BindingSlices.INCLUDES]?.values?.forEach { directive ->
        val targetClass = context[BindingSlices.INTERFACE, directive.targetName] ?: return@forEach
        val mixin = context[BindingSlices.MIXIN, directive.mixinName]
        context[BindingSlices.INTERFACE, directive.targetName] = targetClass + mixin
    }
}

private fun mergePartialInterfaces(context: MutableBindingContext) {
    context[BindingSlices.PARTIAL_INTERFACE]?.forEach { (key, value) ->
        val main = context[BindingSlices.INTERFACE, key] ?: return@forEach
        context[BindingSlices.INTERFACE, key] = main + value
    }
}

private fun mergePartialDictionaries(context: MutableBindingContext) {
    context[BindingSlices.PARTIAL_DICTIONARY]?.forEach { (key, value) ->
        val main = context[BindingSlices.DICTIONARY, key] ?: return@forEach
        context[BindingSlices.DICTIONARY, key] = main + value
    }
}

private fun flattenDictionaryInheritance(context: MutableBindingContext) {
    val dictionaries = context[BindingSlices.DICTIONARY] ?: return

    fun collectDictionaryMembers(name: String): List<InterfaceMember> {
        val dict = dictionaries[name] ?: return emptyList()
        return dict.superTypes.fold(dict.members) { acc, parent ->
            acc + collectDictionaryMembers(parent)
        }
    }

    val flattenedUpdates = dictionaries.mapNotNull { (name, descriptor) ->
        if (descriptor.superTypes.isEmpty()) return@mapNotNull null

        val allMembers = collectDictionaryMembers(name)
        name to descriptor.copy(members = allMembers, superTypes = emptySet())
    }.toMap()

    dictionaries.putAll(flattenedUpdates)
}

private fun filterExternalSuperTypes(context: MutableBindingContext) {
    context[BindingSlices.INTERFACE]?.forEach { (key, value) ->
        val externalTypes = value.superTypes.filter { context[BindingSlices.INTERFACE, it] == null }
        val newTypes = value.superTypes - externalTypes.toSet() + "JsAny"
        context[BindingSlices.INTERFACE, key] = value.copy(superTypes = newTypes)
    }
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
                    val newParamType = param.type.unrollTypedefs(context).resolveUnions(context)
                    param.copy(type = newParamType)
                }
                member.copy(returnType = newReturn, parameters = newParams)
            }
        }
    }

    val interfaces = context[BindingSlices.INTERFACE] ?: emptyMap()
    interfaces.forEach { (name, descriptor) ->
        val resolvedMembers = descriptor.members.map { resolveMember(it) }
        context[BindingSlices.INTERFACE, name] = descriptor.copy(members = resolvedMembers)
    }

    val dictionaries = context[BindingSlices.DICTIONARY] ?: emptyMap()
    dictionaries.forEach { (name, descriptor) ->
        val resolvedMembers = descriptor.members.map { resolveMember(it) }
        context[BindingSlices.DICTIONARY, name] = descriptor.copy(members = resolvedMembers)
    }
}
