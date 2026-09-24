package com.cdodi.webidl.backend

import com.cdodi.webidl.model.BindingContext
import com.cdodi.webidl.model.BindingSlices
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec

/**
 * One file per kind of definition, each with the helpers that belong to it:
 * - `Enums.kt`: enum types and their values
 * - `Interfaces.kt`: interfaces, union marker interfaces, and mixin members on platform types (Navigator.gpu)
 * - `Dictionaries.kt`: dictionary types and their factory functions
 * - `Namespaces.kt`: namespace objects with their constants
 * - `Suspend.kt`: suspend wrappers of Promise-returning operations
 *
 * Empty files are not written. [fileNamePrefix] turns `Enums.kt` into e.g. `WebGpuEnums.kt`.
 */
fun generateKotlin(
    context: BindingContext,
    generatedPackageName: String,
    runtimePackage: String,
    fileNamePrefix: String,
): List<FileSpec> {
    val fileComment = "Current file is generated, please don't modify it manually because your changes will be lost."
    val fileAnnotation = AnnotationSpec.builder(Suppress::class).addMember(
        "%S, %S, %S, %S, %S",
        "Unused",
        "RedundantVisibilityModifier",
        "RemoveRedundantBackticks",
        "ObjectPropertyName",
        "RemoveRedundantQualifierName"
    ).build()

    fun file(kind: String) = FileSpec.builder(generatedPackageName, fileNamePrefix + kind)
        .addFileComment(fileComment)
        .addAnnotation(fileAnnotation)

    val enums = file("Enums")
    val interfaces = file("Interfaces")
    val dictionaries = file("Dictionaries")
    val namespaces = file("Namespaces")
    val suspendWrappers = file("Suspend")

    context[BindingSlices.ENUM]?.values?.forEach { enumDesc ->
        enums.addType(enumDesc.asEnumPoet(generatedPackageName))
        enumDesc.enumValues(generatedPackageName).forEach(enums::addProperty)
    }

    context[BindingSlices.INTERFACE]?.values?.forEach { interfaceDesc ->
        interfaces.addType(interfaceDesc.asInterfacePoet(context, generatedPackageName))
        interfaceDesc.suspendWrappers(context, generatedPackageName, runtimePackage).forEach(suspendWrappers::addFunction)
    }

    context[BindingSlices.EXTERNAL_INCLUDES]?.forEach { (externalName, mixinNames) ->
        val external = context[BindingSlices.EXTERNAL_TYPE, externalName] ?: return@forEach
        mixinNames.mapNotNull { context[BindingSlices.INTERFACE, it] }.forEach { mixin ->
            mixin.extensionMembersOn(external, context, generatedPackageName).forEach { member ->
                when (member) {
                    is PropertySpec -> interfaces.addProperty(member)
                    is FunSpec -> interfaces.addFunction(member)
                }
            }
        }
    }

    context[BindingSlices.DICTIONARY]?.values?.forEach { dictDesc ->
        dictionaries.addType(dictDesc.asDictionaryPoet(context, generatedPackageName))
        dictionaries.addFunction(dictDesc.dictFactory(context, generatedPackageName, runtimePackage))
    }

    context[BindingSlices.NAMESPACE]?.values?.forEach { namespaceDesc ->
        namespaces.addType(namespaceDesc.asNamespacePoet(context, generatedPackageName))
    }

    return listOf(enums, interfaces, dictionaries, namespaces, suspendWrappers)
        .map(FileSpec.Builder::build)
        .filter { it.members.isNotEmpty() }
}
