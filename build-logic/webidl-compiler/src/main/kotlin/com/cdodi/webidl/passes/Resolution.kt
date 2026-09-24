package com.cdodi.webidl.passes

import com.cdodi.webidl.model.BindingContext
import com.cdodi.webidl.model.Descriptor
import com.cdodi.webidl.model.IdlDefinitions
import com.cdodi.webidl.model.InterfaceMember
import com.cdodi.webidl.model.ResolvedBindingContext

/**
 * The middle end: a chain of pure passes, each taking an [IdlDefinitions] snapshot and returning a new one.
 * Order matters and matches the generated output: mixin members come before partial-interface members.
 */
fun resolveSemantics(collected: BindingContext): ResolvedBindingContext =
    IdlDefinitions.from(collected)
        .let(::applyMixins)
        .let(::mergePartials)
        .let(::flattenDictionaries)
        .let(::dropUnknownSuperTypes)
        .let(::resolveTypes)
        .toResolvedContext()

/** `A includes M;` copies the members of mixin M into interface A. */
private fun applyMixins(definitions: IdlDefinitions): IdlDefinitions {
    val interfaces = definitions.interfaces.toMutableMap()
    for (directive in definitions.includes) {
        val target = interfaces[directive.targetName] ?: continue
        interfaces[directive.targetName] = target + definitions.mixins[directive.mixinName]
    }
    return definitions.copy(interfaces = interfaces)
}

private fun mergePartials(definitions: IdlDefinitions): IdlDefinitions = definitions.copy(
    interfaces = definitions.interfaces.mergedWith(definitions.partialInterfaces),
    dictionaries = definitions.dictionaries.mergedWith(definitions.partialDictionaries),
)

private fun Map<String, Descriptor.InterfaceDescriptor>.mergedWith(
    partials: Map<String, Descriptor.InterfaceDescriptor>,
): Map<String, Descriptor.InterfaceDescriptor> = mapValues { (name, main) -> main + partials[name] }

/** Kotlin external interfaces for dictionaries carry every inherited member themselves. */
private fun flattenDictionaries(definitions: IdlDefinitions): IdlDefinitions {
    val dictionaries = definitions.dictionaries
    val flattened = HashMap<String, List<InterfaceMember>>()

    fun membersOf(name: String): List<InterfaceMember> = flattened[name] ?: run {
        val dictionary = dictionaries[name] ?: return emptyList()
        dictionary.superTypes.fold(dictionary.members) { members, parent -> members + membersOf(parent) }
            .also { flattened[name] = it }
    }

    return definitions.copy(
        dictionaries = dictionaries.mapValues { (name, dictionary) ->
            if (dictionary.superTypes.isEmpty()) dictionary else dictionary.copy(members = membersOf(name), superTypes = emptySet())
        }
    )
}

/** Supertypes defined outside these files (EventTarget, DOMException, ...) are dropped for now. */
private fun dropUnknownSuperTypes(definitions: IdlDefinitions): IdlDefinitions = definitions.copy(
    interfaces = definitions.interfaces.mapValues { (_, descriptor) ->
        val known = descriptor.superTypes.filterTo(LinkedHashSet()) { it in definitions.interfaces }
        descriptor.copy(superTypes = known.ifEmpty { setOf("JsAny") })
    }
)

/**
 * Unrolls typedefs and normalises unions in every member of every interface, dictionary and namespace.
 *
 * A union whose members are all interfaces or dictionaries becomes a marker interface that each member extends.
 * The markers are collected first and applied afterwards, so no map is modified while it is being read.
 */
private fun resolveTypes(definitions: IdlDefinitions): IdlDefinitions {
    val markers = LinkedHashMap<String, List<String>>() // marker name -> member type names, in order of first use

    fun resolve(type: Descriptor.TypeDescriptor) = type.unrollTypedefs(definitions.typedefs).normalizeUnions(definitions, markers)

    fun resolveMember(member: InterfaceMember): InterfaceMember = when (member) {
        is InterfaceMember.VariableDescriptor -> member.copy(type = resolve(member.type))
        is InterfaceMember.FunctionDescriptor -> member.copy(
            returnType = resolve(member.returnType),
            parameters = member.parameters.map { it.copy(type = resolve(it.type)) },
        )
        is InterfaceMember.ConstantDescriptor -> member.copy(type = member.type.unrollTypedefs(definitions.typedefs))
    }

    fun Map<String, Descriptor.InterfaceDescriptor>.resolved() =
        mapValues { (_, descriptor) -> descriptor.copy(members = descriptor.members.map(::resolveMember)) }

    val interfaces = definitions.interfaces.resolved()
    val dictionaries = definitions.dictionaries.resolved()
    val namespaces = definitions.namespaces.resolved()

    fun Map<String, Descriptor.InterfaceDescriptor>.withMarkerSuperTypes() = mapValues { (name, descriptor) ->
        val ownMarkers = markers.filterValues { name in it }.keys
        if (ownMarkers.isEmpty()) descriptor else descriptor.copy(superTypes = descriptor.superTypes - "JsAny" + ownMarkers)
    }

    val markerInterfaces = markers.keys
        .filter { it !in interfaces }
        .associateWith { Descriptor.InterfaceDescriptor(name = it, members = emptyList(), superTypes = setOf("JsAny")) }

    return definitions.copy(
        interfaces = interfaces.withMarkerSuperTypes() + markerInterfaces,
        dictionaries = dictionaries.withMarkerSuperTypes(),
        namespaces = namespaces,
    )
}

fun Descriptor.TypeDescriptor.unrollTypedefs(
    typedefs: Map<String, Descriptor.TypeDescriptor>,
    visited: Set<String> = emptySet(),
): Descriptor.TypeDescriptor {
    check(name !in visited) { "Circular typedef detected: ${(visited + name).joinToString(" -> ")}" }
    val typedef = typedefs[name]
    if (typedef != null) return typedef.unrollTypedefs(typedefs, visited + name).copy(isNullable = isNullable || typedef.isNullable)

    val nextVisited = visited + name
    return copy(
        unionMembers = unionMembers.map { it.unrollTypedefs(typedefs, nextVisited) },
        sequenceOf = sequenceOf?.unrollTypedefs(typedefs, nextVisited),
        promiseOf = promiseOf?.unrollTypedefs(typedefs, nextVisited),
        record = record?.entries?.associate {
            it.key.unrollTypedefs(typedefs, nextVisited) to it.value.unrollTypedefs(typedefs, nextVisited)
        }
    )
}

/** Replaces a union by its marker interface (all members are objects) or by `object`, recording markers it creates. */
private fun Descriptor.TypeDescriptor.normalizeUnions(
    definitions: IdlDefinitions,
    markers: MutableMap<String, List<String>>,
): Descriptor.TypeDescriptor {
    val sequence = sequenceOf?.normalizeUnions(definitions, markers)
    val promise = promiseOf?.normalizeUnions(definitions, markers)
    val record = record?.entries?.associate {
        it.key.normalizeUnions(definitions, markers) to it.value.normalizeUnions(definitions, markers)
    }
    if (unionMembers.isEmpty()) return copy(sequenceOf = sequence, promiseOf = promise, record = record)

    val members = unionMembers.map { it.normalizeUnions(definitions, markers) }
    val allObjects = members.all {
        it.name in definitions.interfaces || it.name in definitions.dictionaries || it.name in markers
    }

    return if (allObjects) {
        val markerName = members.joinToString(separator = "Or") { it.name }
        markers.putIfAbsent(markerName, members.map { it.name })
        Descriptor.TypeDescriptor(name = markerName, isNullable = isNullable || members.any { it.isNullable })
    } else {
        // Not every member is an interface or dictionary, so there is no marker type: plain JsAny, non-null unless the IDL says `?`.
        Descriptor.TypeDescriptor(name = "object", isNullable = isNullable)
    }
}
